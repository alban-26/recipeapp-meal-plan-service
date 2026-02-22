package com.myapp.mealplan.adapter.database.mealplan;

import com.my.common.api.UserId;
import com.myapp.mealplan.adapter.MealPlanConverter;
import com.myapp.mealplan.adapter.database.recipe.RecipeInfoMapper;
import com.myapp.mealplan.domain.model.DailyMealPlan;
import com.myapp.mealplan.domain.model.MealPlan;
import com.myapp.mealplan.domain.model.MealPlanId;
import com.myapp.mealplan.domain.service.MealPlanRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class MealPlanRepositoryImpl implements MealPlanRepository {

    private final MealPlanConverter mealPlanConverter;

    private final MealPlanMapper mealPlanMapper;

    private final RecipeInfoMapper recipeInfoMapper;

    @Inject
    public MealPlanRepositoryImpl(MealPlanConverter mealPlanConverter, MealPlanMapper mealPlanMapper, RecipeInfoMapper recipeInfoMapper) {
        this.mealPlanConverter = mealPlanConverter;
        this.mealPlanMapper = mealPlanMapper;
        this.recipeInfoMapper = recipeInfoMapper;
    }

    @Override
    public Optional<MealPlan> findById(MealPlanId mealPlanId) {
        MealPlanEntity mealPlanEntity = mealPlanMapper.selectMealPlanById(mealPlanId.id());
        List<DailyMealPlanEntity> dailyMealPlanEntities = mealPlanMapper.selectDailyMealPlansWithMeals(mealPlanId.id());
        mealPlanEntity.setDailyMealPlans(dailyMealPlanEntities);
        return Optional.ofNullable(mealPlanConverter.entityToDomain(mealPlanEntity));
    }

    @Override
    public List<MealPlan> findAll() {
        List<MealPlanEntity> mealPlanEntities = mealPlanMapper.selectAllMealPlans();
        mealPlanEntities.forEach(element -> element.setDailyMealPlans(findDailyMealPlans(element.getId())));
        return mealPlanEntities.stream().map(mealPlanConverter::entityToDomain).toList();
    }

    @Override
    public List<MealPlan> findAllByUser(UserId userId) {
        List<MealPlanEntity> mealPlanEntities = mealPlanMapper.selectAllMealPlansByUser(userId.value());
        mealPlanEntities.forEach(element -> element.setDailyMealPlans(findDailyMealPlans(element.getId())));
        return mealPlanEntities.stream().map(mealPlanConverter::entityToDomain).toList();
    }

    List<DailyMealPlanEntity> findDailyMealPlans(Long mealPlanId) {
        return mealPlanMapper.selectDailyMealPlansWithMeals(mealPlanId);
    }

    @Override
    public MealPlan save(MealPlan mealPlan) {
        MealPlanEntity mealPlanEntity = mealPlanConverter.domainToEntity(mealPlan);

        mealPlanMapper.insertMealPlan(mealPlanEntity);

        mealPlanEntity.getDailyMealPlans().forEach(dailyMealPlan -> {
            dailyMealPlan.setMealPlanId(mealPlanEntity.getId());
            insertDailyMealPlan(dailyMealPlan, mealPlan.userId().value());
        });

        return mealPlanConverter.entityToDomain(mealPlanEntity);
    }

    private void insertDailyMealPlan(DailyMealPlanEntity dailyMealPlanEntity, String userId) {

        mealPlanMapper.insertDailyMealPlan(dailyMealPlanEntity);

        dailyMealPlanEntity.getDailyMeals().forEach(dailyMeal -> {

            dailyMeal.setDailyMealPlanId(dailyMealPlanEntity.getId());
            if (dailyMeal.getRecipeId() == null || dailyMeal.getRecipeId().equals(0L)) {
                Long recipeId = recipeInfoMapper.findRecipeIdByName(dailyMeal.getRecipeName(), userId);
                dailyMeal.setRecipeId(recipeId);
            }
            mealPlanMapper.insertDailyMeal(dailyMeal);
        });
    }


    @Override
    public MealPlan update(MealPlan mealPlan) {
        MealPlanEntity mealPlanEntity = mealPlanConverter.domainToEntity(mealPlan);

        mealPlanMapper.updateMealPlan(mealPlanEntity);

        mealPlanMapper.deleteDailyMealPlanByMealPlanId(mealPlanEntity.getId());

        mealPlanEntity.getDailyMealPlans().forEach(daily -> insertDailyMealPlan(daily, mealPlan.userId().value()));

        return mealPlanConverter.entityToDomain(mealPlanEntity);
    }


    public DailyMealPlan update(DailyMealPlan dailyMealPlan, MealPlanId mealPlanId) {
        DailyMealPlanEntity dailyMealPlanEntity = mealPlanConverter.mapDailyMealPlan(dailyMealPlan, mealPlanId.id());
        dailyMealPlanEntity.getDailyMeals().forEach(this::updateDailyMeal);
        return mealPlanConverter.mapToDailyMealPlan(dailyMealPlanEntity);
    }

    public void deleteDailyMealPlan(DailyMealPlan dailyMealPlan) {
        mealPlanMapper.deleteDailyMealPlan(dailyMealPlan.id().id());
    }

    public void updateDailyMeal(DailyMealEntity dailyMealEntity) {
        mealPlanMapper.updateDailyMeal(dailyMealEntity);
    }

    @Override
    public void deleteById(MealPlanId mealPlanId) {

        mealPlanMapper.deleteMealPlan(mealPlanId.id());

    }
}
