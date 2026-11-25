package com.myapp.mealplan.adapter.database.recipe;

import com.my.common.api.UserId;
import com.myapp.mealplan.adapter.RecipeInfoConverter;
import com.myapp.mealplan.domain.model.recipe.RecipeId;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;
import com.myapp.mealplan.domain.service.RecipeInfoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RecipeInfoRepositoryImpl implements RecipeInfoRepository {

    private final RecipeInfoMapper mapper;
    private final RecipeInfoConverter recipeInfoConverter;

    @Inject
    public RecipeInfoRepositoryImpl(RecipeInfoMapper mapper, RecipeInfoConverter recipeInfoConverter) {
        this.mapper = mapper;
        this.recipeInfoConverter = recipeInfoConverter;
    }

    @Override
    public Optional<RecipeInfo> findById(RecipeId recipeId) {
        RecipeInfoEntity entity = mapper.selectRecipeInfo(recipeId.id());
        return Optional.ofNullable(recipeInfoConverter.entityToDomain(entity));
    }

    @Override
    public List<RecipeInfo> findAll() {

        List<RecipeInfoEntity> recipeInfoEntities = mapper.selectRecipes();

        return recipeInfoEntities.stream()
                .map(recipeInfoConverter::entityToDomain)
                .toList();
    }

    @Override
    public List<RecipeInfo> findAllByUser(UserId userId) {
        List<RecipeInfoEntity> recipeInfoEntities = mapper.selectRecipesByUser(userId.value());

        return recipeInfoEntities.stream()
                .map(recipeInfoConverter::entityToDomain)
                .toList();
    }

    @Override
    public RecipeInfo save(RecipeInfo recipeInfo) {
        RecipeInfoEntity entity = recipeInfoConverter.domainToEntity(recipeInfo);
        mapper.insertRecipe(entity);
        // 3️⃣ Insert ingredients (ON CONFLICT DO NOTHING) & set ingredientId
        for (RecipeIngredientEntity ingredient : entity.getRecipeIngredients()) {
            ingredient.setRecipeId(entity.getId());

            IngredientEntity ingredientEntity = new IngredientEntity(0L, ingredient.getIngredientName(), ingredient.getIngredientCategory());
            // Insert or ignore if already exists, return ID
            mapper.insertIngredient(
                    ingredientEntity);

            // If insert returned null, fetch id by name
            if (ingredientEntity.getId() != 0) {
                ingredient.setIngredientId(ingredientEntity.getId());
            } else {
                ingredient.setIngredientId(mapper.findByName(ingredient.getIngredientName()));
            }

            // Insert recipe_ingredient
            mapper.insertRecipeIngredient(ingredient);
        }
        return recipeInfoConverter.entityToDomain(entity);
    }

    @Override
    public RecipeInfo update(RecipeInfo recipeInfo) {
        RecipeInfoEntity entity = recipeInfoConverter.domainToEntity(recipeInfo);
        mapper.updateRecipe(entity);

        mapper.deleteRecipeIngredientsByRecipeId(entity.getId());

        // 3. Re-insert all recipe ingredients
        for (RecipeIngredientEntity ingredient : entity.getRecipeIngredients()) {
            ingredient.setRecipeId(entity.getId());
            IngredientEntity ingredientEntity = new IngredientEntity(0L, ingredient.getIngredientName(), ingredient.getIngredientCategory());
            // Insert or ignore if already exists, return ID
            mapper.insertIngredient(
                    ingredientEntity);



            // If insert returned null, fetch id by name
            if (ingredientEntity.getId() != 0) {
                ingredient.setIngredientId(ingredientEntity.getId());
            } else {
                ingredient.setIngredientId(mapper.findByName(ingredient.getIngredientName()));
            }
            mapper.insertRecipeIngredient(ingredient);
        }


        return recipeInfoConverter.entityToDomain(entity);
    }

    @Override
    public void deleteById(RecipeId recipeId) {
        mapper.deleteRecipe(recipeId.id());
    }

    @Override
    public Optional<Long> findIdByName(String recipeName, UserId userId) {
        return Optional.ofNullable(mapper.findRecipeIdByName(recipeName, userId.value()));
    }

    @Override
    public boolean existsByIdAndUserId(RecipeId recipeId, UserId userId) {
        return mapper.existsByIdAndUserId(recipeId.id(), userId.value());
    }
}
