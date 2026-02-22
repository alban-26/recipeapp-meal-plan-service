package com.myapp.mealplan.domain.service;


import com.my.common.api.Repository;
import com.myapp.mealplan.domain.model.DailyMealPlan;
import com.myapp.mealplan.domain.model.MealPlan;
import com.myapp.mealplan.domain.model.MealPlanId;

public interface MealPlanRepository extends Repository<MealPlan, MealPlanId> {

    DailyMealPlan update(DailyMealPlan dailyMealPlan, MealPlanId mealPlanId);

    void deleteDailyMealPlan(DailyMealPlan dailyMealPlan);
}
