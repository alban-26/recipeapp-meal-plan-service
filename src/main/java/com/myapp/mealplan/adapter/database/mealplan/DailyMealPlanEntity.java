package com.myapp.mealplan.adapter.database.mealplan;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DailyMealPlanEntity {
    private Long id;
    private Long mealPlanId;
    private LocalDate date;

    // children
    private List<DailyMealEntity> dailyMeals;
}
