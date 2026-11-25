package com.myapp.adapter.messaging;

import com.my.common.api.UserId;
import com.myapp.EmbeddedPostgresResource;
import com.myapp.mealplan.adapter.messaging.mealplan.MealPlanCreatedEvent;
import com.myapp.mealplan.adapter.messaging.recipe.RecipeCreatedConsumer;
import com.myapp.mealplan.adapter.messaging.recipe.RecipeCreatedEvent;
import com.myapp.mealplan.adapter.messaging.recipe.RecipeInfoDeserializer;
import com.myapp.mealplan.application.MealPlanAccess;
import com.myapp.mealplan.application.RecipeInfoAccess;
import com.myapp.mealplan.domain.model.DailyMealPlan;
import com.myapp.mealplan.domain.model.Meal;
import com.myapp.mealplan.domain.model.MealPlan;
import com.myapp.mealplan.domain.model.MealPlanId;
import com.myapp.mealplan.domain.model.Mealtime;
import com.myapp.mealplan.domain.model.recipe.Ingredient;
import com.myapp.mealplan.domain.model.recipe.ProductCategory;
import com.myapp.mealplan.domain.model.recipe.RecipeId;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;
import com.myapp.mealplan.domain.model.recipe.RecipeIngredient;
import com.myapp.mealplan.domain.model.recipe.Unit;
import com.myapp.mealplan.domain.service.MessagePublisher;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.reactive.messaging.Message;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abgleich der Rezepte mit dem recipe-Dienst: anlegen, aendern, umbenennen,
 * loeschen - und zwar je Nutzer getrennt.
 *
 * Testdaten (V2__insert_data.sql): Nutzer "1" hat die Rezepte "Garlic Chicken" (1),
 * "Spaghetti Pomodoro" (2) und "Chicken & Broccoli Rice Bowl" (3) sowie Plan 1,
 * dessen erster Tag (1) "Garlic Chicken" als DINNER hat. Diese Rezepte kennen
 * die ID im recipe-Dienst noch nicht - genau wie der Bestand vor dieser Aenderung.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(EmbeddedPostgresResource.class)
class RecipeSyncTest {

    // Wortgleich in RabbitMQPublisherTest des recipe-Dienstes: so sehen dessen Meldungen aus.
    private static final String WIRE_RENAMED = "{\"id\":5,\"name\":\"Crepes\",\"portions\":2,"
            + "\"ingredients\":[{\"ingredient\":{\"name\":\"Mehl\",\"productCategory\":\"BAKERY\"},\"quantity\":250.0,\"unit\":\"GRAM\"}],"
            + "\"userId\":{\"value\":\"user-1\"},\"previousName\":\"Pfannkuchen\",\"deleted\":false}";
    private static final String WIRE_DELETED = "{\"id\":5,\"name\":\"Crepes\",\"portions\":2,"
            + "\"ingredients\":[{\"ingredient\":{\"name\":\"Mehl\",\"productCategory\":\"BAKERY\"},\"quantity\":250.0,\"unit\":\"GRAM\"}],"
            + "\"userId\":{\"value\":\"user-1\"},\"previousName\":null,\"deleted\":true}";
    // Meldung eines recipe-Dienstes vor dieser Aenderung: ohne previousName und deleted.
    private static final String WIRE_OLD_FORMAT = "{\"id\":5,\"name\":\"Pfannkuchen\",\"portions\":2,"
            + "\"ingredients\":[{\"ingredient\":{\"name\":\"Mehl\",\"productCategory\":\"BAKERY\"},\"quantity\":250.0,\"unit\":\"GRAM\"}],"
            + "\"userId\":{\"value\":\"user-1\"}}";

    private static final String OWNER = "1";
    private static final String OTHER = "2";
    private static final long PLAN = 1L;
    private static final long FIRST_DAY = 1L;
    private static final long GARLIC_CHICKEN = 1L;

    @InjectMock
    JsonWebToken jwt;

    @InjectMock
    MessagePublisher messagePublisher;

    @Inject
    RecipeCreatedConsumer consumer;

    @Inject
    RecipeInfoDeserializer deserializer;

    @Inject
    RecipeInfoAccess recipeInfoAccess;

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

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    private static RecipeCreatedEvent.RecipeCreatedEventBuilder recipe(long idInRecipeService, String name, String userId) {
        return RecipeCreatedEvent.builder()
                .id(idInRecipeService)
                .name(name)
                .portions(2)
                .ingredients(List.of(new RecipeIngredient(new Ingredient("Mehl", ProductCategory.BAKERY), 250, Unit.GRAM)))
                .userId(new UserId(userId));
    }

    private void created(long idInRecipeService, String name, String userId) {
        consumer.consume(recipe(idInRecipeService, name, userId).build());
    }

    private void renamed(long idInRecipeService, String name, String previousName, String userId) {
        consumer.consume(recipe(idInRecipeService, name, userId).previousName(previousName).build());
    }

    private void deleted(long idInRecipeService, String name, String userId) {
        consumer.consume(recipe(idInRecipeService, name, userId).deleted(true).build());
    }

    private RecipeCreatedEvent fromWire(String json) {
        return (RecipeCreatedEvent) deserializer.convert(Message.of(json), RecipeCreatedEvent.class).getPayload();
    }

    /** Die Auswahlliste, wie die App sie abruft. */
    private List<String> offered(String userId) {
        Mockito.when(jwt.getSubject()).thenReturn(userId);
        return given()
                .accept(ContentType.JSON)
                .when()
                .get("/mealPlans/recipes")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("name", String.class).stream().sorted().toList();
    }

    private long idOf(String name, String userId) {
        return recipeInfoAccess.getIdByName(name, new UserId(userId)).orElseThrow();
    }

    private static Map<String, Object> meal(long recipeId, String name, int servings) {
        return Map.of("recipe", Map.of("id", recipeId, "name", name), "servings", servings);
    }

    private long planWith(String userId, String mealtime, List<Map<String, Object>> meals) {
        Mockito.when(jwt.getSubject()).thenReturn(userId);
        return given()
                .contentType(ContentType.JSON)
                .queryParam("generateShoppingList", false)
                .body(Map.of("id", 0, "startDate", "2026-01-05", "endDate", "2026-01-05", "dailyMealPlans", List.of(
                        Map.of("id", 1767600000000L, "date", "2026-01-05", "meals", Map.of(mealtime, meals)))))
                .when()
                .post("/mealPlans")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private List<Meal> meals(long planId) {
        MealPlan plan = mealPlanAccess.getById(new MealPlanId(planId)).orElseThrow();
        return plan.dailyMealPlans().stream()
                .flatMap(daily -> daily.meals().values().stream())
                .flatMap(List::stream)
                .toList();
    }

    private Meal firstDinnerOfSeededPlan() {
        DailyMealPlan day = mealPlanAccess.getById(new MealPlanId(PLAN)).orElseThrow().dailyMealPlans().stream()
                .filter(daily -> daily.id().id() == FIRST_DAY)
                .findFirst()
                .orElseThrow();
        return day.meals().get(Mealtime.DINNER).getFirst();
    }

    // ------------------------------------------------------------------
    // Punkt 3: Namen je Nutzer
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void twoUsersCanHaveRecipesWithTheSameName() {
        created(10, "Pfannkuchen", OWNER);
        created(20, "Pfannkuchen", OTHER);
        created(21, "Garlic Chicken", OTHER);

        assertTrue(offered(OWNER).contains("Pfannkuchen"));
        assertEquals(List.of("Garlic Chicken", "Pfannkuchen"), offered(OTHER));
        assertNotEquals(idOf("Pfannkuchen", OWNER), idOf("Pfannkuchen", OTHER));
        assertNotEquals(GARLIC_CHICKEN, idOf("Garlic Chicken", OTHER));
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void changesOfOneUserNeverTouchAnotherUsersRecipes() {
        created(10, "Pfannkuchen", OWNER);
        // dieselbe ID bei einem anderen Nutzer, z. B. nach einem Neuaufsetzen des recipe-Dienstes
        created(10, "Waffeln", OTHER);
        // Umbenennung, deren "bisheriger Name" zufaellig einem fremden Rezept gehoert
        renamed(55, "Gekapert", "Garlic Chicken", OTHER);
        deleted(10, "Waffeln", OTHER);
        deleted(77, "Spaghetti Pomodoro", OTHER);

        assertEquals(List.of("Chicken & Broccoli Rice Bowl", "Garlic Chicken", "Pfannkuchen", "Spaghetti Pomodoro"), offered(OWNER));
        assertEquals(List.of("Gekapert"), offered(OTHER));
    }

    // ------------------------------------------------------------------
    // Punkt 4: aendern und umbenennen
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void changedRecipeKeepsItsEntry() {
        created(10, "Pfannkuchen", OWNER);
        long id = idOf("Pfannkuchen", OWNER);

        consumer.consume(recipe(10, "Pfannkuchen", OWNER)
                .portions(6)
                .ingredients(List.of(
                        new RecipeIngredient(new Ingredient("Mehl", ProductCategory.BAKERY), 500, Unit.GRAM),
                        new RecipeIngredient(new Ingredient("Milch", ProductCategory.DAIRY), 1, Unit.LITER)))
                .build());

        assertEquals(id, idOf("Pfannkuchen", OWNER));
        RecipeInfo stored = recipeInfoAccess.getById(new RecipeId(id)).orElseThrow();
        assertEquals(6, stored.portions());
        assertEquals(2, stored.recipeIngredients().size());
        assertEquals(1, offered(OWNER).stream().filter("Pfannkuchen"::equals).count());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void renamedRecipeKeepsItsEntryAndItsPlaceInPlans() {
        created(10, "Pfannkuchen", OWNER);
        long id = idOf("Pfannkuchen", OWNER);
        long plan = planWith(OWNER, "LUNCH", List.of(meal(id, "Pfannkuchen", 2)));

        renamed(10, "Crepes", "Pfannkuchen", OWNER);

        assertEquals(List.of("Chicken & Broccoli Rice Bowl", "Crepes", "Garlic Chicken", "Spaghetti Pomodoro"), offered(OWNER));
        assertEquals(id, idOf("Crepes", OWNER));
        assertEquals("Crepes", meals(plan).getFirst().recipe().name());
        assertEquals(id, meals(plan).getFirst().recipe().id().id());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void renameIsRecognizedByIdEvenWithoutPreviousName() {
        created(10, "Pfannkuchen", OWNER);
        long id = idOf("Pfannkuchen", OWNER);

        // z. B. wenn die Meldung mit dem bisherigen Namen verloren ging
        created(10, "Crepes", OWNER);

        assertEquals(id, idOf("Crepes", OWNER));
        assertFalse(offered(OWNER).contains("Pfannkuchen"));
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void existingRecipeIsFoundByItsPreviousNameWhenRenamed() {
        // "Garlic Chicken" stammt aus der Zeit vor dieser Aenderung und kennt seine ID noch nicht.
        renamed(77, "Knoblauchhuhn", "Garlic Chicken", OWNER);

        assertEquals(List.of("Chicken & Broccoli Rice Bowl", "Knoblauchhuhn", "Spaghetti Pomodoro"), offered(OWNER));
        assertEquals(GARLIC_CHICKEN, idOf("Knoblauchhuhn", OWNER));
        assertEquals("Knoblauchhuhn", firstDinnerOfSeededPlan().recipe().name());

        // ab jetzt ist es an seiner ID erkennbar
        created(77, "Huhn mit Knoblauch", OWNER);
        assertEquals(GARLIC_CHICKEN, idOf("Huhn mit Knoblauch", OWNER));
        assertEquals(3, offered(OWNER).size());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void existingRecipeIsFoundByNameWhenOnlyChanged() {
        created(77, "Garlic Chicken", OWNER);

        assertEquals(GARLIC_CHICKEN, idOf("Garlic Chicken", OWNER));
        assertEquals(3, offered(OWNER).size());
        RecipeInfo stored = recipeInfoAccess.getById(new RecipeId(GARLIC_CHICKEN)).orElseThrow();
        assertEquals(List.of("Mehl"), stored.recipeIngredients().stream().map(i -> i.ingredient().name()).toList());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void recipeTakesOverTypedDishOfTheSameName() {
        long plan = planWith(OWNER, "LUNCH", List.of(meal(0, "Crepes", 2)));
        long typedId = meals(plan).getFirst().recipe().id().id();

        created(10, "Crepes", OWNER);

        assertEquals(typedId, idOf("Crepes", OWNER));
        assertEquals(1, offered(OWNER).stream().filter("Crepes"::equals).count());
        assertEquals(1, recipeInfoAccess.getById(new RecipeId(typedId)).orElseThrow().recipeIngredients().size(),
                "Das eingetippte Gericht hat jetzt die Zutaten des Rezepts");
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void renamingOntoTypedDishMergesBoth() {
        created(10, "Pfannkuchen", OWNER);
        long id = idOf("Pfannkuchen", OWNER);
        // Derselbe Mittag: einmal das Rezept, einmal ein eingetipptes "Crepes".
        long sameMeal = planWith(OWNER, "LUNCH", List.of(meal(id, "Pfannkuchen", 3), meal(0, "Crepes", 2)));
        long typedOnly = planWith(OWNER, "DINNER", List.of(meal(0, "Crepes", 4)));

        renamed(10, "Crepes", "Pfannkuchen", OWNER);

        assertEquals(1, offered(OWNER).stream().filter("Crepes"::equals).count());
        assertFalse(offered(OWNER).contains("Pfannkuchen"));
        assertEquals(id, idOf("Crepes", OWNER));

        List<Meal> merged = meals(sameMeal);
        assertEquals(1, merged.size(), "Aus zwei Eintraegen desselben Gerichts wird einer");
        assertEquals(5, merged.getFirst().servings(), "Die Portionen werden addiert");
        assertEquals(id, merged.getFirst().recipe().id().id());

        List<Meal> moved = meals(typedOnly);
        assertEquals(1, moved.size());
        assertEquals(4, moved.getFirst().servings());
        assertEquals(id, moved.getFirst().recipe().id().id());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void nameClashBetweenTwoRecipesDoesNotBreakTheSync() {
        created(10, "Pfannkuchen", OWNER);
        created(11, "Waffeln", OWNER);
        long first = idOf("Pfannkuchen", OWNER);
        long second = idOf("Waffeln", OWNER);

        // Rezept 10 wurde umbenannt, die Meldung dazu fehlt; nun heisst Rezept 11 "Pfannkuchen".
        renamed(11, "Pfannkuchen", "Waffeln", OWNER);

        assertEquals(second, idOf("Pfannkuchen", OWNER));
        assertEquals(1, offered(OWNER).stream().filter("Pfannkuchen"::equals).count());

        // Die naechste Meldung zu Rezept 10 bringt es mit seinem richtigen Namen zurueck.
        created(10, "Eierkuchen", OWNER);
        assertEquals(first, idOf("Eierkuchen", OWNER));
        assertTrue(offered(OWNER).containsAll(List.of("Eierkuchen", "Pfannkuchen")));
    }

    // ------------------------------------------------------------------
    // Punkt 4: loeschen
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void deletedRecipeIsNoLongerOffered() {
        created(10, "Pfannkuchen", OWNER);
        long id = idOf("Pfannkuchen", OWNER);

        deleted(10, "Pfannkuchen", OWNER);

        assertFalse(offered(OWNER).contains("Pfannkuchen"));
        assertTrue(recipeInfoAccess.getById(new RecipeId(id)).isEmpty(), "Unbenutzt: der Eintrag ist ganz weg");
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void deletedRecipeStaysInExistingPlans() {
        created(77, "Garlic Chicken", OWNER);
        deleted(77, "Garlic Chicken", OWNER);

        assertEquals(List.of("Chicken & Broccoli Rice Bowl", "Spaghetti Pomodoro"), offered(OWNER));
        // Der bestehende Plan zeigt das Gericht weiter ...
        assertEquals("Garlic Chicken", firstDinnerOfSeededPlan().recipe().name());

        // ... laesst sich weiter speichern ...
        Mockito.when(jwt.getSubject()).thenReturn(OWNER);
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("id", FIRST_DAY, "date", "2025-09-29", "meals",
                        Map.of("DINNER", List.of(meal(GARLIC_CHICKEN, "Garlic Chicken", 4)))))
                .when()
                .put("/mealPlans/" + PLAN + "/dailyMealPlans/" + FIRST_DAY)
                .then()
                .statusCode(200);
        assertEquals(4, firstDinnerOfSeededPlan().servings());

        // ... und liefert weiter eine Einkaufsliste.
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("id", PLAN, "startDate", "2025-09-29", "endDate", "2025-09-29", "dailyMealPlans", List.of(
                        Map.of("id", FIRST_DAY, "date", "2025-09-29", "meals",
                                Map.of("DINNER", List.of(meal(GARLIC_CHICKEN, "Garlic Chicken", 2)))))))
                .when()
                .post("/mealPlans/generateShoppingList")
                .then()
                .statusCode(200);
        ArgumentCaptor<MealPlanCreatedEvent> event = ArgumentCaptor.forClass(MealPlanCreatedEvent.class);
        Mockito.verify(messagePublisher).publishMealPlanIngredients(event.capture());
        assertFalse(event.getValue().getRecipeIngredients().isEmpty());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void existingRecipeIsFoundByNameWhenDeleted() {
        // kennt seine ID im recipe-Dienst noch nicht
        deleted(77, "Spaghetti Pomodoro", OWNER);

        assertEquals(List.of("Chicken & Broccoli Rice Bowl", "Garlic Chicken"), offered(OWNER));
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void nameOfDeletedRecipeCanBeUsedAgain() {
        created(77, "Garlic Chicken", OWNER);
        deleted(77, "Garlic Chicken", OWNER);

        // neues Rezept gleichen Namens
        created(88, "Garlic Chicken", OWNER);

        assertEquals(1, offered(OWNER).stream().filter("Garlic Chicken"::equals).count());
        assertNotEquals(GARLIC_CHICKEN, idOf("Garlic Chicken", OWNER));
        // der alte Plan zeigt weiter auf das alte Rezept
        assertEquals(GARLIC_CHICKEN, firstDinnerOfSeededPlan().recipe().id().id());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void typedDishNamedLikeDeletedRecipeIsAFreshDish() {
        created(77, "Garlic Chicken", OWNER);
        deleted(77, "Garlic Chicken", OWNER);

        long plan = planWith(OWNER, "LUNCH", List.of(meal(0, "Garlic Chicken", 2)));

        long typedId = meals(plan).getFirst().recipe().id().id();
        assertNotEquals(GARLIC_CHICKEN, typedId);
        assertTrue(recipeInfoAccess.getById(new RecipeId(typedId)).orElseThrow().recipeIngredients().isEmpty(),
                "Das eingetippte Gericht erbt nicht die Zutaten des geloeschten Rezepts");
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void deletingAnUnknownRecipeIsIgnored() {
        deleted(999, "Gibt es nicht", OWNER);

        assertEquals(3, offered(OWNER).size());
    }

    // ------------------------------------------------------------------
    // Meldungen, wie sie vom recipe-Dienst kommen
    // ------------------------------------------------------------------

    @Test
    void readsRenameMessage() {
        RecipeCreatedEvent event = fromWire(WIRE_RENAMED);

        assertEquals(5L, event.getId());
        assertEquals("Crepes", event.getName());
        assertEquals(2, event.getPortions());
        assertEquals("Mehl", event.getIngredients().getFirst().ingredient().name());
        assertEquals(Unit.GRAM, event.getIngredients().getFirst().unit());
        assertEquals("user-1", event.getUserId().value());
        assertEquals("Pfannkuchen", event.getPreviousName());
        assertFalse(event.isDeleted());
    }

    @Test
    void readsDeleteMessage() {
        RecipeCreatedEvent event = fromWire(WIRE_DELETED);

        assertEquals(5L, event.getId());
        assertEquals("user-1", event.getUserId().value());
        assertNull(event.getPreviousName());
        assertTrue(event.isDeleted());
    }

    @Test
    void readsMessageOfOlderRecipeService() {
        RecipeCreatedEvent event = fromWire(WIRE_OLD_FORMAT);

        assertEquals("Pfannkuchen", event.getName());
        assertNull(event.getPreviousName());
        assertFalse(event.isDeleted());
    }

    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void messagesFromTheWireAreApplied() {
        consumer.consume(fromWire(WIRE_OLD_FORMAT));
        assertEquals(List.of("Pfannkuchen"), offered("user-1"));

        consumer.consume(fromWire(WIRE_RENAMED));
        assertEquals(List.of("Crepes"), offered("user-1"));

        consumer.consume(fromWire(WIRE_DELETED));
        assertEquals(List.of(), offered("user-1"));
    }
}
