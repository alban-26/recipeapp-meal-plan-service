package com.myapp.mealplan.adapter.database.recipe;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RecipeInfoMapper {

    // ================================
    // Recipe
    // ================================
    @Insert("INSERT INTO recipe (name, portions, user_id) " +
            "VALUES (#{name}, #{portions}, #{userId}) RETURNING id")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insertRecipe(RecipeInfoEntity entity);

    // Ueber die ID, nicht ueber den Namen: sonst liesse sich ein Rezept nicht
    // umbenennen, und gleichnamige Rezepte anderer Nutzer wuerden mitgeaendert.
    // Ein nur ausgeblendeter Eintrag wird dabei wieder sichtbar.
    @Update("UPDATE recipe SET name = #{name}, portions = #{portions}, deleted = FALSE " +
            "WHERE id = #{id}")
    void updateRecipe(RecipeInfoEntity entity);


    @Delete("DELETE FROM recipe WHERE id = #{id}")
    void deleteRecipe(Long id);


    // ================================
    // Ingredient dictionary
    // ================================
    @Insert("INSERT INTO ingredient (name, category) VALUES (#{name}, #{category})" +
            "ON CONFLICT (name) DO NOTHING ")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertIngredient(IngredientEntity entity);

    @Select("SELECT id, name, category FROM ingredient WHERE id = #{id}")
    IngredientEntity selectIngredientById(Long id);

    @Select("SELECT id, name, category FROM ingredient")
    List<IngredientEntity> selectAllIngredients();


    // ================================
    // Recipe ↔ Ingredient link
    // ================================
    @Insert("INSERT INTO recipe_ingredient (recipe_id, ingredient_id, total_quantity, unit) " +
            "VALUES (#{recipeId}, #{ingredientId}, #{totalQuantity}, #{unit})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertRecipeIngredient(RecipeIngredientEntity entity);

    @Select("SELECT ri.id AS recipeIngredientId, ri.recipe_id AS recipeId, " +
            "ri.ingredient_id AS ingredientId, i.name AS ingredientName, i.category AS ingredientCategory, " +
            "ri.total_quantity AS totalQuantity, ri.unit AS unit " +
            "FROM recipe_ingredient ri " +
            "JOIN ingredient i ON ri.ingredient_id = i.id " +
            "WHERE ri.recipe_id = #{recipeId}")
    @Results({
            @Result(property = "id", column = "recipeIngredientId"),
            @Result(property = "recipeId", column = "recipeId"),
            @Result(property = "ingredientId", column = "ingredientId"),
            @Result(property = "ingredientName", column = "ingredientName"),
            @Result(property = "ingredientCategory", column = "ingredientCategory"),
            @Result(property = "totalQuantity", column = "totalQuantity"),
            @Result(property = "unit", column = "unit")
    })
    List<RecipeIngredientEntity> selectRecipeIngredients(Long recipeId);

    @Delete("DELETE FROM recipe_ingredient WHERE recipe_id = #{recipeId}")
    void deleteRecipeIngredientsByRecipeId(Long recipeId);



    @Select("SELECT id FROM ingredient WHERE name = #{name}")
    Long findByName(@Param("name") String name);

    @Select("SELECT id FROM recipe WHERE name = #{recipeName} and user_id = #{userId} AND NOT deleted")
    Long findRecipeIdByName(String recipeName, String userId);

    // ================================
    // Abgleich mit dem recipe-Dienst
    // ================================

    @Select("SELECT id FROM recipe WHERE source_recipe_id = #{sourceRecipeId} AND user_id = #{userId}")
    Long findRecipeIdBySourceId(@Param("sourceRecipeId") Long sourceRecipeId, @Param("userId") String userId);

    /** Eintrag ohne Bezug zum recipe-Dienst: frei eingetipptes Gericht oder Altbestand. */
    @Select("SELECT id FROM recipe WHERE name = #{recipeName} AND user_id = #{userId} " +
            "AND NOT deleted AND source_recipe_id IS NULL")
    Long findUnlinkedRecipeIdByName(@Param("recipeName") String recipeName, @Param("userId") String userId);

    @Select("SELECT source_recipe_id FROM recipe WHERE id = #{id}")
    Long findSourceRecipeId(@Param("id") Long id);

    @Update("UPDATE recipe SET source_recipe_id = #{sourceRecipeId} WHERE id = #{id}")
    void updateSourceRecipeId(@Param("id") Long id, @Param("sourceRecipeId") Long sourceRecipeId);

    /** Ausblenden, Bezug behalten: die naechste Meldung zu diesem Rezept blendet es wieder ein. */
    @Update("UPDATE recipe SET deleted = TRUE WHERE id = #{id}")
    void hideRecipe(@Param("id") Long id);

    /** Im recipe-Dienst geloescht, wird aber noch von Essensplaenen gebraucht. */
    @Update("UPDATE recipe SET deleted = TRUE, source_recipe_id = NULL WHERE id = #{id}")
    void markRecipeDeleted(@Param("id") Long id);

    @Select("SELECT EXISTS(SELECT 1 FROM daily_meals WHERE recipe_id = #{id})")
    boolean isRecipeUsedInMealPlans(@Param("id") Long id);

    // Zusammenfuehren zweier Eintraege: Mahlzeiten von fromId gehen auf intoId ueber.
    // Steht in derselben Mahlzeit schon intoId, werden die Portionen addiert
    // (daily_meals erlaubt ein Rezept je Tag und Mahlzeit nur einmal).

    @Update("UPDATE daily_meals keep SET servings = keep.servings + dup.servings " +
            "FROM daily_meals dup " +
            "WHERE keep.recipe_id = #{intoId} AND dup.recipe_id = #{fromId} " +
            "AND dup.daily_meal_plan_id = keep.daily_meal_plan_id AND dup.meal_time = keep.meal_time")
    void addServingsOfDuplicateMeals(@Param("fromId") Long fromId, @Param("intoId") Long intoId);

    @Delete("DELETE FROM daily_meals dup USING daily_meals keep " +
            "WHERE dup.recipe_id = #{fromId} AND keep.recipe_id = #{intoId} " +
            "AND dup.daily_meal_plan_id = keep.daily_meal_plan_id AND dup.meal_time = keep.meal_time")
    void deleteDuplicateMeals(@Param("fromId") Long fromId, @Param("intoId") Long intoId);

    @Update("UPDATE daily_meals SET recipe_id = #{intoId} WHERE recipe_id = #{fromId}")
    void moveMeals(@Param("fromId") Long fromId, @Param("intoId") Long intoId);

    @Select("SELECT EXISTS(SELECT 1 FROM recipe WHERE id = #{recipeId} AND user_id = #{userId})")
    boolean existsByIdAndUserId(@Param("recipeId") Long recipeId, @Param("userId") String userId);



    // ================================
    // Fetch full Recipe with nested children
    // ================================
    @Select("SELECT id AS recipeId, name, portions, user_ID FROM recipe WHERE id = #{recipeId}")
    @Results({
            @Result(property = "id", column = "recipeId"),
            @Result(property = "name", column = "name"),
            @Result(property = "portions", column = "portions"),
            @Result(property = "recipeIngredients", javaType = List.class, column = "recipeId",
                    many = @Many(select = "selectRecipeIngredients"))
    })
    RecipeInfoEntity selectRecipeInfo(Long recipeId);

    @Select("SELECT id AS recipeId, name, portions FROM recipe")
    @Results({
            @Result(property = "id", column = "recipeId"),
            @Result(property = "name", column = "name"),
            @Result(property = "portions", column = "portions"),
            @Result(property = "recipeIngredients", javaType = List.class, column = "recipeId",
                    many = @Many(select = "selectRecipeIngredients"))
    })
    List<RecipeInfoEntity> selectRecipes();

    // Auswahlliste des Nutzers: ohne geloeschte Rezepte.
    @Select("SELECT id AS recipeId, name, portions, user_id FROM recipe WHERE user_id = #{userId} AND NOT deleted")
    @Results({
            @Result(property = "id", column = "recipeId"),
            @Result(property = "name", column = "name"),
            @Result(property = "portions", column = "portions"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "recipeIngredients", javaType = List.class, column = "recipeId",
                    many = @Many(select = "selectRecipeIngredients"))
    })
    List<RecipeInfoEntity> selectRecipesByUser(String userId);



}
