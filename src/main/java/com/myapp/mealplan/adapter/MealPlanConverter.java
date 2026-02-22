package com.myapp.mealplan.adapter;

import com.my.common.api.DtoConverter;
import com.my.common.api.EntityConverter;
import com.my.common.api.EventConverter;
import com.myapp.mealplan.adapter.database.mealplan.DailyMealPlanEntity;
import com.myapp.mealplan.adapter.database.mealplan.MealPlanEntity;
import com.myapp.mealplan.adapter.messaging.mealplan.MealPlanCreatedEvent;
import com.myapp.mealplan.application.RecipeInfoAccess;
import com.myapp.mealplan.domain.model.DailyMealPlan;
import com.myapp.mealplan.domain.model.MealPlan;
import org.openapitools.model.DailyMealPlanDto;
import org.openapitools.model.MealPlanDto;


public interface MealPlanConverter extends EntityConverter<MealPlan, MealPlanEntity>, EventConverter<MealPlan, MealPlanCreatedEvent>, DtoConverter<MealPlan, MealPlanDto> {

    DailyMealPlanEntity mapDailyMealPlan(DailyMealPlan dailyMealPlan, Long mealPlanId);

    DailyMealPlan mapToDailyMealPlan(DailyMealPlanEntity entity);

    MealPlanCreatedEvent domainToEvent(MealPlan mealPlan, RecipeInfoAccess recipeInfoAccess);

    DailyMealPlan dtoToDomain(DailyMealPlanDto dto);

    DailyMealPlanDto domainToDto(DailyMealPlan domain);
}
