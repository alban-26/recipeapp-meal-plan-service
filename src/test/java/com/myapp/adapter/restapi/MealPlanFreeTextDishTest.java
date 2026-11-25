package com.myapp.adapter.restapi;

import com.my.common.api.UserId;
import com.myapp.EmbeddedPostgresResource;
import com.myapp.mealplan.adapter.messaging.mealplan.MealPlanCreatedEvent;
import com.myapp.mealplan.application.MealPlanAccess;
import com.myapp.mealplan.application.RecipeInfoAccess;
import com.myapp.mealplan.domain.model.Meal;
import com.myapp.mealplan.domain.model.MealPlan;
import com.myapp.mealplan.domain.model.MealPlanId;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;
import com.myapp.mealplan.domain.service.MessagePublisher;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frei eingetippte Gerichte (Rezept-ID 0) in Essensplaenen.
 *
 * Testdaten (V2__insert_data.sql): die Rezepte 1-3 gehoeren Nutzer "1",
 * Rezept 1 ist "Garlic Chicken" mit u. a. "Chicken Breast" als Zutat.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(EmbeddedPostgresResource.class)
class MealPlanFreeTextDishTest {

    private static final String OWNER = "1";
    private static final String OTHER = "2";
    private static final long GARLIC_CHICKEN = 1L;

    @InjectMock
    JsonWebToken jwt;

    @InjectMock
    MessagePublisher messagePublisher;

    @Inject
    MealPlanAccess mealPlanAccess;

    @Inject
    RecipeInfoAccess recipeInfoAccess;

    @Inject
    Flyway flyway;

    @BeforeEach
    void setupDatabase() {
        flyway.clean();
        flyway.migrate();
    }

    @AfterAll
    void cleanupAfterTests() {
        flyway.clean();
    }

    // --- Derselbe Aufbau, den die App schickt ---

    private static Map<String, Object> typedMeal(String dishName, int servings) {
        return Map.of("recipe", Map.of("id", 0, "name", dishName), "servings", servings);
    }

    private static Map<String, Object> day(long id, String date, String mealtime, Map<String, Object> meal) {
        return Map.of("id", id, "date", date, "meals", Map.of(mealtime, List.of(meal)));
    }

    private static Map<String, Object> plan(List<Map<String, Object>> days) {
        return Map.of("id", 0, "startDate", "2026-01-05", "endDate", "2026-01-06", "dailyMealPlans", days);
    }

    private long createPlan(String userId, boolean generateShoppingList, Map<String, Object> body) {
        Mockito.when(jwt.getSubject()).thenReturn(userId);
        return given()
                .contentType(ContentType.JSON)
                .queryParam("generateShoppingList", generateShoppingList)
                .body(body)
                .when()
                .post("/mealPlans")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private List<Meal> storedMeals(long planId) {
        MealPlan plan = mealPlanAccess.getById(new MealPlanId(planId)).orElseThrow();
        return plan.dailyMealPlans().stream()
                .flatMap(daily -> daily.meals().values().stream())
                .flatMap(List::stream)
                .toList();
    }

    private List<String> recipeNames(String userId) {
        return recipeInfoAccess.getAllByUser(new UserId(userId)).stream()
                .map(RecipeInfo::name)
                .sorted()
                .toList();
    }

    private List<String> publishedShoppingList() {
        ArgumentCaptor<MealPlanCreatedEvent> event = ArgumentCaptor.forClass(MealPlanCreatedEvent.class);
        Mockito.verify(messagePublisher).publishMealPlanIngredients(event.capture());
        return event.getValue().getRecipeIngredients().stream()
                .map(ingredient -> ingredient.ingredient().name())
                .sorted()
                .toList();
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void typedDishWithShoppingList() {
        long id = createPlan(OWNER, true, plan(List.of(
                day(1767600000000L, "2026-01-05", "LUNCH", typedMeal("Resteessen", 2)))));

        assertEquals("Resteessen", storedMeals(id).getFirst().recipe().name());
        // Ein Gericht ohne Zutaten landet mit seinem Namen auf der Einkaufsliste.
        assertEquals(List.of("Resteessen"), publishedShoppingList());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void typedDishNamedLikeOwnRecipeUsesThatRecipe() {
        long id = createPlan(OWNER, true, plan(List.of(
                day(1767600000000L, "2026-01-05", "DINNER", typedMeal("Garlic Chicken", 2)))));

        assertEquals(GARLIC_CHICKEN, storedMeals(id).getFirst().recipe().id().id());
        assertEquals(List.of("Chicken & Broccoli Rice Bowl", "Garlic Chicken", "Spaghetti Pomodoro"), recipeNames(OWNER),
                "Es darf kein zweites Rezept mit demselben Namen entstehen");
        assertTrue(publishedShoppingList().contains("Chicken Breast"),
                "Die Einkaufsliste nimmt die Zutaten des vorhandenen Rezepts");
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void sameTypedDishTwiceInOnePlan() {
        long id = createPlan(OWNER, false, plan(List.of(
                day(1767600000000L, "2026-01-05", "LUNCH", typedMeal("Resteessen", 2)),
                day(1767600000001L, "2026-01-06", "DINNER", typedMeal("Resteessen", 3)))));

        List<Meal> meals = storedMeals(id);
        assertEquals(2, meals.size());
        assertEquals(meals.get(0).recipe().id(), meals.get(1).recipe().id());
        assertEquals(1, recipeNames(OWNER).stream().filter("Resteessen"::equals).count());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void typedDishCanBeUsedAgainInALaterPlan() {
        long first = createPlan(OWNER, false, plan(List.of(
                day(1767600000000L, "2026-01-05", "LUNCH", typedMeal("Resteessen", 2)))));
        long second = createPlan(OWNER, false, plan(List.of(
                day(1767600000000L, "2026-01-05", "LUNCH", typedMeal("Resteessen", 4)))));

        assertEquals(storedMeals(first).getFirst().recipe().id(), storedMeals(second).getFirst().recipe().id());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void twoUsersCanTypeTheSameDish() {
        long ownersPlan = createPlan(OWNER, false, plan(List.of(
                day(1767600000000L, "2026-01-05", "LUNCH", typedMeal("Resteessen", 2)))));
        long othersPlan = createPlan(OTHER, false, plan(List.of(
                day(1767600000000L, "2026-01-05", "LUNCH", typedMeal("Resteessen", 2)))));

        assertNotEquals(storedMeals(ownersPlan).getFirst().recipe().id(), storedMeals(othersPlan).getFirst().recipe().id());
        assertEquals(List.of("Resteessen"), recipeNames(OTHER));
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void typedDishNamedLikeSomeoneElsesRecipeStaysSeparate() {
        long id = createPlan(OTHER, true, plan(List.of(
                day(1767600000000L, "2026-01-05", "DINNER", typedMeal("Garlic Chicken", 2)))));

        assertNotEquals(GARLIC_CHICKEN, storedMeals(id).getFirst().recipe().id().id());
        assertEquals(List.of("Garlic Chicken"), recipeNames(OTHER));
        // Nur der Name des Gerichts - nicht die Zutaten des fremden Rezepts.
        assertEquals(List.of("Garlic Chicken"), publishedShoppingList());
        assertFalse(recipeNames(OWNER).isEmpty());
    }
}
