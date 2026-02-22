package com.myapp.mealplan.adapter;

import com.my.common.api.DtoConverter;
import com.my.common.api.EntityConverter;
import com.my.common.api.EventConverter;
import com.myapp.mealplan.adapter.database.recipe.RecipeInfoEntity;
import com.myapp.mealplan.adapter.messaging.recipe.RecipeCreatedEvent;
import com.myapp.mealplan.domain.model.recipe.Recipe;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;
import org.openapitools.model.RecipeDto;

public interface RecipeInfoConverter extends EventConverter<RecipeInfo, RecipeCreatedEvent>, EntityConverter<RecipeInfo, RecipeInfoEntity>, DtoConverter<Recipe, RecipeDto> {
}
