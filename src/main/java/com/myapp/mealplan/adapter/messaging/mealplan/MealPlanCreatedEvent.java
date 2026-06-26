package com.myapp.mealplan.adapter.messaging.mealplan;

import com.my.common.api.UserId;
import com.myapp.mealplan.domain.model.recipe.RecipeIngredient;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.Collection;

@AllArgsConstructor
@Data
public class MealPlanCreatedEvent {

    Collection<RecipeIngredient> recipeIngredients;
    LocalDate startDate;
    LocalDate endDate;
    UserId userId;
}
