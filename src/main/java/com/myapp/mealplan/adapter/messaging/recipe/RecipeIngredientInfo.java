package com.myapp.mealplan.adapter.messaging.recipe;


import com.myapp.mealplan.domain.model.recipe.ProductCategory;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecipeIngredientInfo {

    private String name;
    private ProductCategory category;
    private Double quantity;
    private String unit;

}

