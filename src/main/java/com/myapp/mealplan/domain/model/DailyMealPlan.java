package com.myapp.mealplan.domain.model;


import lombok.With;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;

@With
public record DailyMealPlan(DailyMealPlanId id, LocalDate date,
        EnumMap<Mealtime, List<Meal>> meals
                            ) {
}
