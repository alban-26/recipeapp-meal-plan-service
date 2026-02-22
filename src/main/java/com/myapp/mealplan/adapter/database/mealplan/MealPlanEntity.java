package com.myapp.mealplan.adapter.database.mealplan;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class MealPlanEntity {
    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    private String userId;

    // children
    private List<DailyMealPlanEntity> dailyMealPlans;
}
