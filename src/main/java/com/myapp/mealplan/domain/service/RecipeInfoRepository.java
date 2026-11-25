package com.myapp.mealplan.domain.service;


import com.my.common.api.Repository;
import com.my.common.api.UserId;
import com.myapp.mealplan.domain.model.recipe.RecipeId;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;

import java.util.Optional;

public interface RecipeInfoRepository extends Repository<RecipeInfo, RecipeId> {

    Optional<Long> findIdByName(String recipeName, UserId userId);

    boolean existsByIdAndUserId(RecipeId recipeId, UserId userId);

}
