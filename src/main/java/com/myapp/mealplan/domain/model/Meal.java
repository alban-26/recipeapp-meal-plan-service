package com.myapp.mealplan.domain.model;

import com.myapp.mealplan.domain.model.recipe.Recipe;
import lombok.With;

@With
public record Meal(Recipe recipe, int servings) {
}
