package com.myapp.mealplan.domain.model.recipe;

import lombok.experimental.Delegate;

import java.util.Collection;

public record RecipeInfo(@Delegate Recipe recipe, int portions, Collection<RecipeIngredient> recipeIngredients) {
}
