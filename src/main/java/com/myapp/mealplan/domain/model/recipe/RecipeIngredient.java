package com.myapp.mealplan.domain.model.recipe;

public record RecipeIngredient(Ingredient ingredient, double quantity, Unit unit) {

    public RecipeIngredient scaledForServings(int currentPortions, int newServings) {
        if (currentPortions <= 0 || newServings <= 0) {
            throw new IllegalArgumentException("Portions and servings must be positive");
        }

        double scaledQuantity = (quantity / (double) currentPortions) * newServings;
        return new RecipeIngredient(ingredient, scaledQuantity, unit);
    }
}