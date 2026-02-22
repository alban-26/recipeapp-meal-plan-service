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

    @Update("UPDATE recipe SET name = #{name}, portions = #{portions} " +
            "WHERE name = #{name}")
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

    @Select("SELECT id FROM recipe WHERE name = #{recipeName} and user_id = #{userId}")
    Long findRecipeIdByName(String recipeName, String userId);



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

    @Select("SELECT id AS recipeId, name, portions, user_id FROM recipe WHERE user_id = #{userId}")
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
