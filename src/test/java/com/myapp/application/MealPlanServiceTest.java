package com.myapp.application;

import com.my.common.api.UserId;
import com.myapp.EmbeddedPostgresResource;
import com.myapp.mealplan.application.MealPlanAccess;
import com.myapp.mealplan.application.RecipeInfoAccess;
import com.myapp.mealplan.domain.model.*;
import com.myapp.mealplan.domain.model.recipe.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static com.myapp.mealplan.domain.model.Mealtime.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(EmbeddedPostgresResource.class)
public class MealPlanServiceTest {

    @Inject
    MealPlanAccess mealPlanAccess;

    @Inject
    RecipeInfoAccess recipeInfoAccess;

    @Inject
    Flyway flyway;

    private static final UserId USER_ID = new UserId("1");


    @BeforeEach
    public void setupDatabase() {
        flyway.clean();
        flyway.migrate();
    }

    @AfterAll
    public void cleanupAfterTests() {
        flyway.clean();
    }

    @Test
    public void testGetIngredientsByMeals() {

        Ingredient flour = new Ingredient("Flour", ProductCategory.BAKERY);
        Ingredient sugar = new Ingredient("Sugar", ProductCategory.BAKERY);

        // Recipe ingredients are per serving
        RecipeIngredient flour50g = new RecipeIngredient(flour, 50, Unit.GRAM);
        RecipeIngredient sugar20g = new RecipeIngredient(sugar, 20, Unit.GRAM);

        // Recipes
        Recipe recipePancake = new Recipe(new RecipeId(0), "Pancake", USER_ID);
        Recipe recipeCake = new Recipe(new RecipeId(0), "Cake", USER_ID);

        // RecipeInfos — what your repository normally stores
        RecipeInfo pancakeInfo = new RecipeInfo(recipePancake, 2, List.of(flour50g, sugar20g));
        RecipeInfo cakeInfo = new RecipeInfo(recipeCake, 2, List.of(flour50g));


        recipeInfoAccess.save(pancakeInfo);
        recipeInfoAccess.save(cakeInfo);

        Collection<RecipeInfo> recipeInfos = recipeInfoAccess.getAll();

        RecipeInfo pancake = recipeInfos.stream().filter(e -> e.name().equals("Pancake")).findAny().get();
        RecipeInfo cake = recipeInfos.stream().filter(e -> e.name().equals("Cake")).findAny().get();


        Meal meal1 = new Meal(pancake.recipe(), 2);
        Meal meal2 = new Meal(cake.recipe(), 3);
        Collection<RecipeIngredient> result = recipeInfoAccess.getIngredientsByMeals(List.of(meal1, meal2));

        Map<String, RecipeIngredient> resultMap = new HashMap<>();
        result.forEach(i -> resultMap.put(i.ingredient().name(), i));

        RecipeIngredient flourResult = resultMap.get("Flour");
        RecipeIngredient sugarResult = resultMap.get("Sugar");


        assertEquals(125.0, flourResult.quantity(), 0.001, "Flour quantity should be summed correctly");
        assertEquals(20.0, sugarResult.quantity(), 0.001, "Sugar quantity should be summed correctly");
        assertEquals(2, result.size(), "Should have exactly two unique ingredients");

    }


    @Test
    void testGetMealPlanById() {
        // when
        Optional<MealPlan> optionalMealPlan = mealPlanAccess.getById(new MealPlanId(1L));

        // then
        assertTrue(optionalMealPlan.isPresent(), "Meal plan should be present for ID 1");
        MealPlan mealPlan = optionalMealPlan.get();

        assertEquals(1L, mealPlan.mealPlanId().id(), "Meal plan ID should be 1");
        assertEquals(LocalDate.of(2025, 9, 29), mealPlan.startDate(), "Start date mismatch");
        assertEquals(LocalDate.of(2025, 10, 5), mealPlan.endDate(), "End date mismatch");

        List<DailyMealPlan> dailyMealPlans = mealPlan.dailyMealPlans();
        assertNotNull(dailyMealPlans, "Daily meal plans list should not be null");
        assertEquals(7, dailyMealPlans.size(), "There should be 7 daily meal plans");

        for (DailyMealPlan daily : dailyMealPlans) {
            assertNotNull(daily.id(), "DailyMealPlan ID should not be null");
            assertNotNull(daily.date(), "DailyMealPlan date should not be null");
            assertFalse(daily.meals().isEmpty(), "DailyMealPlan should have at least one meal");
            assertTrue(
                    !daily.date().isBefore(LocalDate.of(2025, 9, 29)) &&
                            !daily.date().isAfter(LocalDate.of(2025, 10, 5)),
                    "DailyMealPlan date should be within range"
            );
        }

        DailyMealPlan firstDay = dailyMealPlans.get(0);
        assertEquals(LocalDate.of(2025, 9, 29), firstDay.date(), "First day date should match start date");

        Map<Mealtime, List<Meal>> meals = firstDay.meals();
        assertTrue(meals.containsKey(DINNER), "First day should have a DINNER meal");

        List<Meal> dinnerMeals = meals.get(DINNER);
        assertNotNull(dinnerMeals, "Dinner meals list should not be null");
        assertFalse(dinnerMeals.isEmpty(), "Dinner meals list should not be empty");

        Meal dinner = dinnerMeals.get(0);
        assertEquals("Garlic Chicken", dinner.recipe().name(), "Expected recipe name mismatch");
        assertEquals(2, dinner.servings(), "Expected servings mismatch");

        for (DailyMealPlan daily : dailyMealPlans) {
            for (List<Meal> mealList : daily.meals().values()) {
                for (Meal meal : mealList) {
                    assertNotNull(meal.recipe(), "Recipe should not be null");
                    assertNotNull(meal.recipe().name(), "Recipe name should not be null");
                    assertFalse(meal.recipe().name().isBlank(), "Recipe name should not be blank");
                }
            }
        }
    }

    @Test
    void testGetAllMealPlans() {
        // when
        List<MealPlan> mealPlans = mealPlanAccess.getAllByUser(USER_ID);

        // then
        assertNotNull(mealPlans, "Returned meal plans list should not be null");
        assertFalse(mealPlans.isEmpty(), "Meal plans list should not be empty");

        assertEquals(1, mealPlans.size(), "Expected one meal plan in the system");

        MealPlan mealPlan = mealPlans.get(0);

        assertEquals(1L, mealPlan.mealPlanId().id(), "Meal plan ID should be 1");
        assertEquals(LocalDate.of(2025, 9, 29), mealPlan.startDate(), "Start date mismatch");
        assertEquals(LocalDate.of(2025, 10, 5), mealPlan.endDate(), "End date mismatch");

        List<DailyMealPlan> dailyMealPlans = mealPlan.dailyMealPlans();
        assertNotNull(dailyMealPlans, "Daily meal plans list should not be null");
        assertEquals(7, dailyMealPlans.size(), "Expected 7 daily meal plans");

        for (DailyMealPlan daily : dailyMealPlans) {
            assertNotNull(daily.id(), "DailyMealPlan ID should not be null");
            assertNotNull(daily.date(), "DailyMealPlan date should not be null");
            assertFalse(daily.meals().isEmpty(), "DailyMealPlan should have at least one meal");

            assertTrue(
                    !daily.date().isBefore(LocalDate.of(2025, 9, 29)) &&
                            !daily.date().isAfter(LocalDate.of(2025, 10, 5)),
                    "DailyMealPlan date should be within range"
            );
        }

        DailyMealPlan firstDay = dailyMealPlans.get(0);
        assertEquals(LocalDate.of(2025, 9, 29), firstDay.date(), "First day should be 2025-09-29");

        Map<Mealtime, List<Meal>> meals = firstDay.meals();
        assertTrue(meals.containsKey(Mealtime.DINNER), "First day should contain DINNER meal");

        List<Meal> dinnerMeals = meals.get(Mealtime.DINNER);
        assertNotNull(dinnerMeals, "Dinner meals list should not be null");
        assertFalse(dinnerMeals.isEmpty(), "Dinner meals list should not be empty");

        Meal dinner = dinnerMeals.get(0);
        assertEquals("Garlic Chicken", dinner.recipe().name(), "Expected recipe name mismatch");
        assertEquals(2, dinner.servings(), "Expected servings mismatch");

        for (DailyMealPlan daily : dailyMealPlans) {
            for (List<Meal> mealList : daily.meals().values()) {
                for (Meal meal : mealList) {
                    assertNotNull(meal.recipe(), "Recipe should not be null");
                    assertNotNull(meal.recipe().name(), "Recipe name should not be null");
                    assertFalse(meal.recipe().name().isBlank(), "Recipe name should not be blank");
                    assertTrue(meal.servings() > 0, "Servings should be greater than 0");
                }
            }
        }
    }

    @Test
    public void testCreateMealPlan() {
        EnumMap<Mealtime, List<Meal>> meals = new EnumMap<>(Mealtime.class);

        List<RecipeInfo> recipes = recipeInfoAccess.getAll();

        RecipeInfo recipeInfo1 = recipes.get(0);
        RecipeInfo recipeInfo2 = recipes.get(1);
        RecipeInfo recipeInfo3 = recipes.get(2);
        Recipe recipe1 = new Recipe(recipeInfo1.id(), recipeInfo1.name(), USER_ID);
        Recipe recipe2 = new Recipe(recipeInfo2.id(), recipeInfo2.name(), USER_ID);
        Recipe recipe3 = new Recipe(recipeInfo3.id(), recipeInfo3.name(), USER_ID);


        meals.put(Mealtime.BREAKFAST, List.of(
                new Meal(recipe1, 2),
                new Meal(recipe2, 1)
        ));

        meals.put(Mealtime.LUNCH, List.of(
                new Meal(recipe3, 2),
                new Meal(recipe1, 1)
        ));

        meals.put(DINNER, List.of(
                new Meal(recipe1, 2),
                new Meal(recipe3, 3)
        ));



        DailyMealPlan dailyMealPlan1 = new DailyMealPlan(new DailyMealPlanId(0), LocalDate.of(2025, 3, 3), meals);

        MealPlan mealPlan = new MealPlan(new MealPlanId(0), LocalDate.of(2025, 3, 3), LocalDate.of(2025, 3, 10), List.of(dailyMealPlan1), USER_ID);

        mealPlanAccess.save(mealPlan);

        List<MealPlan> mealPlans2 = mealPlanAccess.getAll();

    }

    @Test
    public void testRemoveMealPlanById() {

        List<MealPlan> mealPlans = mealPlanAccess.getAll();

        mealPlanAccess.delete(mealPlans.getFirst());

        List<MealPlan> mealPlans1 = mealPlanAccess.getAll();

        assertTrue(mealPlans1.isEmpty(), "Meal plans should be empty");

    }

    @Test
    void testUpdateMealPlans() {
        // given
        List<MealPlan> mealPlans = mealPlanAccess.getAll();
        assertFalse(mealPlans.isEmpty(), "Expected at least one meal plan");
        MealPlanId mealPlanId = mealPlans.getFirst().mealPlanId();

        List<RecipeInfo> recipes = recipeInfoAccess.getAll();
        assertTrue(recipes.size() >= 2, "Expected at least two recipes for the test");

        RecipeInfo recipeInfo2 = recipes.get(1);
        Recipe recipe2 = new Recipe(recipeInfo2.id(), recipeInfo2.name(), USER_ID);

        EnumMap<Mealtime, List<Meal>> meals = new EnumMap<>(Mealtime.class);
        meals.put(Mealtime.LUNCH, List.of(new Meal(recipe2, 2)));

        DailyMealPlan updatedDay = new DailyMealPlan(
                new DailyMealPlanId(1L),
                LocalDate.of(2025, 9, 29),
                meals
        );

        // when
        mealPlanAccess.updateDailyMealPlan(updatedDay, mealPlanId);

        // then
        Optional<MealPlan> mealPlanUpdatedOpt = mealPlanAccess.getById(mealPlanId);
        assertTrue(mealPlanUpdatedOpt.isPresent(), "Updated meal plan should exist");

        MealPlan mealPlanUpdated = mealPlanUpdatedOpt.get();

        DailyMealPlan firstDay = mealPlanUpdated.dailyMealPlans().stream()
                .filter(d -> d.date().equals(LocalDate.of(2025, 9, 29)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No DailyMealPlan found for 2025-09-29"));

        Map<Mealtime, List<Meal>> updatedMeals = firstDay.meals();

        assertFalse(updatedMeals.containsKey(Mealtime.DINNER), "DINNER should have been replaced");
        assertTrue(updatedMeals.containsKey(Mealtime.LUNCH), "LUNCH should exist after update");

        List<Meal> lunchMeals = updatedMeals.get(Mealtime.LUNCH);
        assertNotNull(lunchMeals, "LUNCH meals list should not be null");
        assertFalse(lunchMeals.isEmpty(), "LUNCH meals list should not be empty");

        Meal lunchMeal = lunchMeals.get(0);
        assertEquals("Spaghetti Pomodoro", lunchMeal.recipe().name(), "Updated meal should be Spaghetti Pomodoro");
        assertEquals(2, lunchMeal.servings(), "Updated servings should be 2");

    }

}
