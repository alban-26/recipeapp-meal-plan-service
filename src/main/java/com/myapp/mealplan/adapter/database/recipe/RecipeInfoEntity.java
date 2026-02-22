package com.myapp.mealplan.adapter.database.recipe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeInfoEntity {

    private Long id;
    private String name;
    private int portions;
    private String userId;
    private List<RecipeIngredientEntity> recipeIngredients;

}