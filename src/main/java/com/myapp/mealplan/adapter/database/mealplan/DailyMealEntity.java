package com.myapp.mealplan.adapter.database.mealplan;

import lombok.Data;

@Data
public class DailyMealEntity {
    private Long id;
    private Long dailyMealPlanId;
    private Long recipeId;
    private String recipeName; // extra from join
    private String mealTime;
    private Integer servings;
}