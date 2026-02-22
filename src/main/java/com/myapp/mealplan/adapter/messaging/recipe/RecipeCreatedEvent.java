package com.myapp.mealplan.adapter.messaging.recipe;


import com.my.common.api.UserId;
import com.myapp.mealplan.domain.model.recipe.RecipeIngredient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class RecipeCreatedEvent {

    private Long id;
    private String name;
    private Integer portions;
    private List<RecipeIngredient> ingredients;
    private UserId userId;

}
