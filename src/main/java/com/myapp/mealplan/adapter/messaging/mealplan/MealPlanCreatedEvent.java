package com.myapp.mealplan.adapter.messaging.mealplan;

import com.my.common.api.UserId;
import com.myapp.mealplan.domain.model.recipe.RecipeIngredient;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collection;

@AllArgsConstructor
@Data
public class MealPlanCreatedEvent {

    Collection<RecipeIngredient> recipeIngredients;
    UserId userId;
}
