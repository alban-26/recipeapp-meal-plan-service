package com.myapp.mealplan.application;

import com.my.common.api.AbstractAccess;
import com.my.common.api.UserId;
import com.my.common.api.pagination.PageRequest;
import com.my.common.api.pagination.PageResult;
import com.myapp.mealplan.adapter.MealPlanConverter;
import com.myapp.mealplan.adapter.messaging.mealplan.MealPlanCreatedEvent;
import com.myapp.mealplan.domain.model.DailyMealPlan;
import com.myapp.mealplan.domain.model.Meal;
import com.myapp.mealplan.domain.model.MealPlan;
import com.myapp.mealplan.domain.model.MealPlanId;
import com.myapp.mealplan.domain.model.recipe.Recipe;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;
import com.myapp.mealplan.domain.service.MealPlanRepository;
import com.myapp.mealplan.domain.service.MessagePublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
@Slf4j
public class MealPlanAccess extends AbstractAccess<MealPlanRepository, MealPlan, MealPlanId> {


    private final MessagePublisher messagePublisher;
    private final RecipeInfoAccess recipeInfoAccess;
    private final MealPlanConverter mealPlanConverter;

    protected MealPlanAccess() {
        super(null); // or use a placeholder, but this is OK for CDI to initialize, and it won't be used
        this.messagePublisher = null;
        this.recipeInfoAccess = null;
        this.mealPlanConverter = null;
    }


    @Inject
    public MealPlanAccess(MealPlanRepository repository, MessagePublisher messagePublisher, RecipeInfoAccess recipeInfoAccess, MealPlanConverter mealPlanConverter) {
        super(repository);
        this.messagePublisher = messagePublisher;
        this.recipeInfoAccess = recipeInfoAccess;
        this.mealPlanConverter = mealPlanConverter;
    }

    @Override
    public PageResult<MealPlan> getAllByUser(UserId userId, PageRequest pageRequest) {
        return null;
    }

    public MealPlan save(MealPlan mealPlan) {
        saveRecipes(mealPlan);

        return super.save(mealPlan);
    }

    private void saveRecipes(MealPlan mealPlan) {
        List<Recipe> recipes =
                mealPlan.dailyMealPlans().stream()
                        .flatMap(plan -> plan.meals().values().stream()) // Collection<List<Meal>>
                        .flatMap(List::stream)                            // Meal
                        .map(Meal::recipe).map(recipe -> recipe.withUserId(mealPlan.userId()))
                        .toList();

        recipes.forEach(this::saveRecipe);
    }

    /**
     * Legt ein frei eingetipptes Gericht (ID 0) an - aber nur, wenn der Nutzer
     * noch keines mit diesem Namen hat. Sonst wird beim Speichern des Plans das
     * vorhandene verwendet; vorher scheiterte der zweite Versuch am eindeutigen
     * Namen (500), z. B. beim selben Gericht an zwei Tagen.
     */
    private void saveRecipe(Recipe recipe) {
        if (recipe.id() != null && recipe.id().id() != 0) {
            return;
        }
        if (recipeInfoAccess.getIdByName(recipe.name(), recipe.userId()).isPresent()) {
            return;
        }
        recipeInfoAccess.save(new RecipeInfo(recipe, 0, List.of()));
    }


    public MealPlan saveAndGenerateShoppingList(@NonNull MealPlan item) {

        // save(...) statt super.save(...): legt vorher die frei eingetippten Gerichte an.
        MealPlan savedMealPlan = save(item);
        log.debug("MealPlan saved with ID: {}", savedMealPlan.mealPlanId());

        MealPlanCreatedEvent mealPlanCreatedEvent = mealPlanConverter.domainToEvent(savedMealPlan, recipeInfoAccess);
        messagePublisher.publishMealPlanIngredients(mealPlanCreatedEvent);
        log.info("MealPlanCreatedEvent published successfully for MealPlanId={}", savedMealPlan.mealPlanId());
        return savedMealPlan;
    }

    public void generateShoppingList(@NonNull MealPlan item) {

        MealPlanCreatedEvent mealPlanCreatedEvent = mealPlanConverter.domainToEvent(item, recipeInfoAccess);
        messagePublisher.publishMealPlanIngredients(mealPlanCreatedEvent);
        log.info("MealPlanCreatedEvent published successfully for MealPlanId={}", item.mealPlanId());
    }


    @Override
    protected boolean isNew(MealPlan item) {
        return item.mealPlanId() == null || item.mealPlanId().id() == 0;
    }

    @Override
    protected MealPlanId getId(MealPlan item) {
        return item.mealPlanId();
    }

    public DailyMealPlan updateDailyMealPlan(@NonNull DailyMealPlan dailyMealPlan, @NonNull MealPlanId mealPlanId) {
        DailyMealPlan updated = repository.update(dailyMealPlan, mealPlanId);
        log.debug("DailyMealPlan with ID \"{}\" updated", updated.id().id());
        return updated;
    }


    public void deleteDailyMealPlan(DailyMealPlan dailyMealPlan) {
        repository.deleteDailyMealPlan(dailyMealPlan);
        log.info("DailyMealPlan with ID \"{}\" deleted successfully", dailyMealPlan.id().id());
    }

}
