package com.myapp.adapter.restapi;

import com.my.common.api.UserId;
import com.myapp.EmbeddedPostgresResource;
import com.myapp.mealplan.adapter.messaging.mealplan.MealPlanCreatedEvent;
import com.myapp.mealplan.application.MealPlanAccess;
import com.myapp.mealplan.domain.model.DailyMealPlan;
import com.myapp.mealplan.domain.model.Meal;
import com.myapp.mealplan.domain.model.MealPlan;
import com.myapp.mealplan.domain.model.MealPlanId;
import com.myapp.mealplan.domain.model.Mealtime;
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
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/**
 * Prueft ueber die echte REST-Schnittstelle und die echte Datenbank, dass
 * Essensplaene nur fuer den Besitzer erreichbar sind.
 *
 * Testdaten (V2__insert_data.sql): Plan 1 (29.09.-05.10.2025, sieben Tage)
 * und die Rezepte 1-3 gehoeren Nutzer "1". Tag 1 ist der 29.09. mit
 * "Garlic Chicken" als DINNER.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(EmbeddedPostgresResource.class)
class MealPlanOwnershipTest {

    private static final String OWNER = "1";
    private static final String STRANGER = "2";
    private static final long PLAN = 1L;
    private static final long FIRST_DAY = 1L;
    private static final long OWNERS_RECIPE = 1L;

    @InjectMock
    JsonWebToken jwt;

    @InjectMock
    MessagePublisher messagePublisher;

    @Inject
    MealPlanAccess mealPlanAccess;

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

    private void actAs(String userId) {
        Mockito.when(jwt.getSubject()).thenReturn(userId);
    }

    // --- Derselbe Aufbau, den die App schickt ---

    private static Map<String, Object> meal(long recipeId, String recipeName, int servings) {
        return Map.of("recipe", Map.of("id", recipeId, "name", recipeName), "servings", servings);
    }

    private static Map<String, Object> day(long id, String date, String mealtime, Map<String, Object> meal) {
        return Map.of("id", id, "date", date, "meals", Map.of(mealtime, List.of(meal)));
    }

    private static Map<String, Object> plan(long id, String start, String end, List<Map<String, Object>> days) {
        return Map.of("id", id, "startDate", start, "endDate", end, "dailyMealPlans", days);
    }

    private MealPlan stored(long id) {
        return mealPlanAccess.getById(new MealPlanId(id)).orElseThrow();
    }

    private DailyMealPlan storedDay(long planId, long dayId) {
        return stored(planId).dailyMealPlans().stream()
                .filter(day -> day.id().id() == dayId)
                .findFirst()
                .orElseThrow();
    }

    private void assertSeededPlanUntouched() {
        MealPlan plan = stored(PLAN);
        assertEquals(OWNER, plan.userId().value());
        assertEquals(LocalDate.of(2025, 9, 29), plan.startDate());
        assertEquals(LocalDate.of(2025, 10, 5), plan.endDate());
        assertEquals(7, plan.dailyMealPlans().size());
        List<Meal> dinner = storedDay(PLAN, FIRST_DAY).meals().get(Mealtime.DINNER);
        assertEquals(1, dinner.size());
        assertEquals("Garlic Chicken", dinner.getFirst().recipe().name());
        assertEquals(2, dinner.getFirst().servings());
    }

    /** Legt fuer den Nutzer einen Plan mit einem selbst eingetippten Gericht an. */
    private long createOwnPlan(String userId, String dishName) {
        actAs(userId);
        return given()
                .contentType(ContentType.JSON)
                .queryParam("generateShoppingList", false)
                .body(plan(0, "2026-01-05", "2026-01-05",
                        List.of(day(1767600000000L, "2026-01-05", "LUNCH", meal(0, dishName, 2)))))
                .when()
                .post("/mealPlans")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    // ------------------------------------------------------------------
    // Plan lesen, aendern, loeschen
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void ownerCanReadOwnPlan() {
        actAs(OWNER);

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/mealPlans/" + PLAN)
                .then()
                .statusCode(200)
                .body("startDate", equalTo("2025-09-29"))
                .body("dailyMealPlans.size()", equalTo(7));
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void strangerCannotReadPlan() {
        actAs(STRANGER);

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/mealPlans/" + PLAN)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void unknownPlanIsNotFound() {
        actAs(OWNER);

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/mealPlans/987654321")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void strangerCannotUpdatePlan() {
        actAs(STRANGER);

        given()
                .contentType(ContentType.JSON)
                .body(plan(PLAN, "2030-01-01", "2030-01-01", List.of()))
                .when()
                .put("/mealPlans/" + PLAN)
                .then()
                .statusCode(404);

        assertSeededPlanUntouched();
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void updateUsesIdFromPathNotFromBody() {
        long own = createOwnPlan(STRANGER, "Nudelsalat");

        // Eigener Plan im Pfad, fremde ID im Body: geaendert wird der eigene.
        given()
                .contentType(ContentType.JSON)
                .body(plan(PLAN, "2030-01-01", "2030-01-01", List.of()))
                .when()
                .put("/mealPlans/" + own)
                .then()
                .statusCode(200);

        assertSeededPlanUntouched();
        MealPlan updated = stored(own);
        assertEquals(LocalDate.of(2030, 1, 1), updated.startDate());
        assertEquals(STRANGER, updated.userId().value());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void createNeverOverwritesExistingPlan() {
        actAs(STRANGER);

        long created = given()
                .contentType(ContentType.JSON)
                .queryParam("generateShoppingList", false)
                .body(plan(PLAN, "2030-01-01", "2030-01-01", List.of()))
                .when()
                .post("/mealPlans")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");

        assertNotEquals(PLAN, created, "POST muss einen neuen Plan anlegen");
        assertSeededPlanUntouched();
        assertEquals(STRANGER, stored(created).userId().value());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void strangerCannotDeletePlan() {
        actAs(STRANGER);

        given()
                .when()
                .delete("/mealPlans/" + PLAN)
                .then()
                .statusCode(404);

        assertSeededPlanUntouched();
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void ownerCanDeleteOwnPlan() {
        actAs(OWNER);

        given()
                .when()
                .delete("/mealPlans/" + PLAN)
                .then()
                .statusCode(204);

        assertTrue(mealPlanAccess.getById(new MealPlanId(PLAN)).isEmpty());
    }

    // ------------------------------------------------------------------
    // Einzelne Tage
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void strangerCannotUpdateDay() {
        long own = createOwnPlan(STRANGER, "Nudelsalat");
        long ownRecipe = stored(own).dailyMealPlans().getFirst().meals().get(Mealtime.LUNCH).getFirst().recipe().id().id();
        Map<String, Object> hijack = day(FIRST_DAY, "2025-09-29", "LUNCH", meal(ownRecipe, "Nudelsalat", 9));

        // direkt ueber den fremden Plan ...
        given()
                .contentType(ContentType.JSON)
                .body(hijack)
                .when()
                .put("/mealPlans/" + PLAN + "/dailyMealPlans/" + FIRST_DAY)
                .then()
                .statusCode(404);

        // ... und ueber den eigenen Plan mit der ID eines fremden Tages.
        given()
                .contentType(ContentType.JSON)
                .body(hijack)
                .when()
                .put("/mealPlans/" + own + "/dailyMealPlans/" + FIRST_DAY)
                .then()
                .statusCode(404);

        assertSeededPlanUntouched();
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void strangerCannotDeleteDay() {
        long own = createOwnPlan(STRANGER, "Nudelsalat");

        given()
                .when()
                .delete("/mealPlans/" + PLAN + "/dailyMealPlans/" + FIRST_DAY)
                .then()
                .statusCode(404);

        given()
                .when()
                .delete("/mealPlans/" + own + "/dailyMealPlans/" + FIRST_DAY)
                .then()
                .statusCode(404);

        assertSeededPlanUntouched();
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void ownerCanUpdateAndDeleteOwnDay() {
        actAs(OWNER);

        given()
                .contentType(ContentType.JSON)
                .body(day(FIRST_DAY, "2025-09-29", "DINNER", meal(2, "Spaghetti Pomodoro", 4)))
                .when()
                .put("/mealPlans/" + PLAN + "/dailyMealPlans/" + FIRST_DAY)
                .then()
                .statusCode(200);

        Meal dinner = storedDay(PLAN, FIRST_DAY).meals().get(Mealtime.DINNER).getFirst();
        assertEquals("Spaghetti Pomodoro", dinner.recipe().name());
        assertEquals(4, dinner.servings());

        given()
                .when()
                .delete("/mealPlans/" + PLAN + "/dailyMealPlans/" + FIRST_DAY)
                .then()
                .statusCode(204);

        assertEquals(6, stored(PLAN).dailyMealPlans().size());
    }

    // ------------------------------------------------------------------
    // Fremde Rezepte in eigenen Plaenen
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void strangerCannotPlanWithForeignRecipe() {
        actAs(STRANGER);
        List<Map<String, Object>> days = List.of(
                day(1767600000000L, "2026-01-05", "DINNER", meal(OWNERS_RECIPE, "egal", 2)));

        given()
                .contentType(ContentType.JSON)
                .queryParam("generateShoppingList", false)
                .body(plan(0, "2026-01-05", "2026-01-05", days))
                .when()
                .post("/mealPlans")
                .then()
                .statusCode(404);

        given()
                .contentType(ContentType.JSON)
                .queryParam("generateShoppingList", true)
                .body(plan(0, "2026-01-05", "2026-01-05", days))
                .when()
                .post("/mealPlans")
                .then()
                .statusCode(404);

        assertTrue(mealPlanAccess.getAllByUser(new UserId(STRANGER)).isEmpty(), "Es darf kein Plan entstanden sein");
        Mockito.verify(messagePublisher, Mockito.never()).publishMealPlanIngredients(any());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void strangerCannotUpdateOwnPlanWithForeignRecipe() {
        long own = createOwnPlan(STRANGER, "Nudelsalat");
        long ownDay = stored(own).dailyMealPlans().getFirst().id().id();

        given()
                .contentType(ContentType.JSON)
                .body(plan(own, "2026-01-05", "2026-01-05", List.of(
                        day(ownDay, "2026-01-05", "DINNER", meal(OWNERS_RECIPE, "egal", 2)))))
                .when()
                .put("/mealPlans/" + own)
                .then()
                .statusCode(404);

        given()
                .contentType(ContentType.JSON)
                .body(day(ownDay, "2026-01-05", "LUNCH", meal(OWNERS_RECIPE, "egal", 2)))
                .when()
                .put("/mealPlans/" + own + "/dailyMealPlans/" + ownDay)
                .then()
                .statusCode(404);

        Meal lunch = storedDay(own, ownDay).meals().get(Mealtime.LUNCH).getFirst();
        assertEquals("Nudelsalat", lunch.recipe().name());
        assertFalse(storedDay(own, ownDay).meals().containsKey(Mealtime.DINNER));
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void strangerCannotGenerateShoppingListFromForeignRecipe() {
        actAs(STRANGER);

        given()
                .contentType(ContentType.JSON)
                .body(plan(0, "2026-01-05", "2026-01-05", List.of(
                        day(1767600000000L, "2026-01-05", "DINNER", meal(OWNERS_RECIPE, "egal", 2)))))
                .when()
                .post("/mealPlans/generateShoppingList")
                .then()
                .statusCode(404);

        Mockito.verify(messagePublisher, Mockito.never()).publishMealPlanIngredients(any());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void ownerCanGenerateShoppingListFromOwnRecipes() {
        actAs(OWNER);

        given()
                .contentType(ContentType.JSON)
                .body(plan(PLAN, "2025-09-29", "2025-09-29", List.of(
                        day(FIRST_DAY, "2025-09-29", "DINNER", meal(OWNERS_RECIPE, "Garlic Chicken", 2)))))
                .when()
                .post("/mealPlans/generateShoppingList")
                .then()
                .statusCode(200);

        Mockito.verify(messagePublisher).publishMealPlanIngredients(any(MealPlanCreatedEvent.class));
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void ownerCanUpdateOwnPlanWithOwnRecipes() {
        actAs(OWNER);

        given()
                .contentType(ContentType.JSON)
                .body(plan(PLAN, "2025-09-29", "2025-09-30", List.of(
                        day(FIRST_DAY, "2025-09-29", "LUNCH", meal(2, "Spaghetti Pomodoro", 3)),
                        day(1767600000000L, "2025-09-30", "DINNER", meal(0, "Resteessen", 2)))))
                .when()
                .put("/mealPlans/" + PLAN)
                .then()
                .statusCode(200)
                .body("id", equalTo((int) PLAN))
                .body("dailyMealPlans.size()", equalTo(2));

        MealPlan plan = stored(PLAN);
        assertEquals(OWNER, plan.userId().value());
        assertEquals(LocalDate.of(2025, 9, 30), plan.endDate());
        assertEquals(2, plan.dailyMealPlans().size());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void planOverviewContainsOnlyOwnPlans() {
        createOwnPlan(STRANGER, "Nudelsalat");

        List<Integer> ids = given()
                .accept(ContentType.JSON)
                .when()
                .get("/mealPlans")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("id", Integer.class);

        assertEquals(1, ids.size());
        assertNotEquals((int) PLAN, ids.getFirst());
    }
}
