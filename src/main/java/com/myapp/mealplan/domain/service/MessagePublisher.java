package com.myapp.mealplan.domain.service;

import com.myapp.mealplan.adapter.messaging.mealplan.MealPlanCreatedEvent;
import com.myapp.mealplan.domain.model.recipe.RecipeIngredient;

import java.util.Collection;

public interface MessagePublisher {
    void publishMealPlanIngredients(MealPlanCreatedEvent event);
}
