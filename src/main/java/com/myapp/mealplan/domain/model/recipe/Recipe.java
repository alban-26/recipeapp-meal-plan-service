package com.myapp.mealplan.domain.model.recipe;

import com.my.common.api.UserId;
import lombok.With;

@With
public record Recipe(RecipeId id, String name, UserId userId) {
}
