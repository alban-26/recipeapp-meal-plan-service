package com.myapp.mealplan.application;


import com.my.common.api.AbstractAccess;
import com.my.common.api.UserId;
import com.myapp.mealplan.domain.model.Meal;
import com.myapp.mealplan.domain.model.recipe.*;
import com.myapp.mealplan.domain.service.RecipeInfoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecipeInfoAccess extends AbstractAccess<RecipeInfoRepository, RecipeInfo, RecipeId> {

    protected RecipeInfoAccess() {
        super(null);
    }

    @Inject
    protected RecipeInfoAccess(RecipeInfoRepository repository) {
        super(repository);
    }

    @Override
    protected boolean isNew(RecipeInfo item) {
        return item.id() == null || item.id().id() == 0;
    }

    @Override
    protected RecipeId getId(RecipeInfo item) {
        return item.id();
    }

    public Collection<RecipeIngredient> getIngredientsByMeals(Collection<Meal> meals) {
        Collection<Collection<RecipeIngredient>> allIngredients = meals.stream().map(this::getIngredientsByMeal).toList();
        return reduceIngredients(allIngredients);
    }

    private static Collection<RecipeIngredient> reduceIngredients(Collection<Collection<RecipeIngredient>> ingredientGroups) {
        return ingredientGroups.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(
                        RecipeIngredient::ingredient,
                        ri -> ri,
                        (a, b) -> new RecipeIngredient(
                                a.ingredient(),
                                a.quantity() + b.quantity(),
                                a.unit()
                        )
                ))
                .values();
    }


    private Collection<RecipeIngredient> getIngredientsByMeal(Meal meal) {
        RecipeInfo recipeInfo =
                repository.findById(meal.recipe().id()).orElseThrow();

        if (recipeInfo.recipeIngredients().isEmpty()) {
            return java.util.List.of(
                    new RecipeIngredient(
                            new Ingredient(recipeInfo.recipe().name(), ProductCategory.OTHER),
                            meal.servings(),
                            Unit.PIECE // oder Unit.PORTION, je nach Domain
                    )
            );
        }

        return recipeInfo.recipeIngredients().stream()
                .map(ingredient ->
                        ingredient.scaledForServings(
                                recipeInfo.portions(),
                                meal.servings()
                        )
                )
                .toList();
    }



    public Optional<Long> getIdByName(String recipeName, UserId userId) {
        return repository.findIdByName(recipeName, userId);
    }

}
