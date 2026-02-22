package com.myapp.mealplan.adapter.database.mealplan;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MealPlanMapper {

    // ================================
    // Meal Plan
    // ================================
    @Insert("INSERT INTO meal_plan (start_date, end_date, user_id) " +
            "VALUES (#{startDate}, #{endDate}, #{userId}) RETURNING id")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insertMealPlan(MealPlanEntity entity);

    @Update("UPDATE meal_plan SET start_date = #{startDate}, end_date = #{endDate} WHERE id = #{id}")
    void updateMealPlan(MealPlanEntity entity);

    @Delete("DELETE FROM meal_plan WHERE id = #{id}")
    void deleteMealPlan(Long id);

    @Select("SELECT id, start_date, end_date, user_id FROM meal_plan WHERE id = #{id}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "startDate", column = "start_date"),
            @Result(property = "endDate", column = "end_date"),
            @Result(property = "userId", column = "user_id")
    })
    MealPlanEntity selectMealPlanById(Long id);

    @Select("SELECT id, start_date, end_date, user_id FROM meal_plan")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "startDate", column = "start_date"),
            @Result(property = "endDate", column = "end_date"),
            @Result(property = "userId", column = "user_id")
    })
    List<MealPlanEntity> selectAllMealPlans();

    @Select("SELECT id, start_date, end_date, user_id FROM meal_plan WHERE user_id = #{userId}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "startDate", column = "start_date"),
            @Result(property = "endDate", column = "end_date"),
            @Result(property = "userId", column = "user_id")
    })
    List<MealPlanEntity> selectAllMealPlansByUser(String userId);


    // ================================
    // Daily Meal Plan
    // ================================
    @Insert("INSERT INTO daily_meal_plan (meal_plan_id, date) " +
            "VALUES (#{mealPlanId}, #{date}) RETURNING id")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insertDailyMealPlan(DailyMealPlanEntity entity);

    @Delete("DELETE FROM daily_meal_plan WHERE id = #{id}")
    void deleteDailyMealPlan(Long id);

    @Delete("DELETE FROM daily_meal_plan WHERE meal_plan_id = #{id}")
    void deleteDailyMealPlanByMealPlanId(Long id);

    @Update("UPDATE daily_meals SET recipe_id = #{recipeId}, meal_time = #{mealTime}, servings=#{servings} WHERE daily_meal_plan_id = #{dailyMealPlanId}")
    void updateDailyMeal(DailyMealEntity dailyMealEntity);


    @Select("SELECT id, meal_plan_id, date FROM daily_meal_plan WHERE meal_plan_id = #{mealPlanId}")
    List<DailyMealPlanEntity> selectDailyMealPlans(Long mealPlanId);


    // ================================
    // Daily Meals
    // ================================
    @Insert("INSERT INTO daily_meals (daily_meal_plan_id, recipe_id, meal_time, servings) " +
            "VALUES (#{dailyMealPlanId}, #{recipeId}, #{mealTime}, #{servings}) RETURNING id")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insertDailyMeal(DailyMealEntity entity);

    @Delete("DELETE FROM daily_meals WHERE id = #{id}")
    void deleteDailyMeal(Long id);

    @Select("SELECT dm.id AS dailyMealId, dm.daily_meal_plan_id AS dailyMealPlanId, " +
            "dm.recipe_id AS recipeId, r.name AS recipeName, dm.meal_time AS mealTime, dm.servings AS servings " +
            "FROM daily_meals dm " +
            "JOIN recipe r ON dm.recipe_id = r.id " +
            "WHERE dm.daily_meal_plan_id = #{dailyMealPlanId}")
    @Results({
            @Result(property = "id", column = "dailyMealId"),
            @Result(property = "dailyMealPlanId", column = "dailyMealPlanId"),
            @Result(property = "recipeId", column = "recipeId"),
            @Result(property = "recipeName", column = "recipeName"),
            @Result(property = "mealTime", column = "mealTime"),
            @Result(property = "servings", column = "servings")
    })
    List<DailyMealEntity> selectDailyMeals(Long dailyMealPlanId);


    // ================================
    // Fetch full MealPlan with nested children
    // ================================
    @Select("SELECT id AS mealPlanId, start_date, end_date FROM meal_plan WHERE id = #{mealPlanId}")
    @Results({
            @Result(property = "id", column = "mealPlanId"),
            @Result(property = "startDate", column = "start_date"),
            @Result(property = "endDate", column = "end_date"),
            @Result(property = "dailyMealPlans", javaType = List.class, column = "mealPlanId",
                    many = @Many(select = "selectDailyMealPlansWithMeals"))
    })
    MealPlanEntity selectMealPlanWithDetails(Long mealPlanId);

    // helper: daily meal plans with meals
    @Select("SELECT id AS dailyMealPlanId, meal_plan_id AS mealPlanId, date " +
            "FROM daily_meal_plan WHERE meal_plan_id = #{mealPlanId}")
    @Results({
            @Result(property = "id", column = "dailyMealPlanId"),
            @Result(property = "mealPlanId", column = "mealPlanId"),
            @Result(property = "date", column = "date"),
            @Result(property = "dailyMeals", javaType = List.class, column = "dailyMealPlanId",
                    many = @Many(select = "selectDailyMeals"))
    })
    List<DailyMealPlanEntity> selectDailyMealPlansWithMeals(Long mealPlanId);
}
