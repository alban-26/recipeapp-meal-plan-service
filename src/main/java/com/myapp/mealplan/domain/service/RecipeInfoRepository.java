package com.myapp.mealplan.domain.service;


import com.my.common.api.Repository;
import com.my.common.api.UserId;
import com.myapp.mealplan.domain.model.recipe.RecipeId;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;

import java.util.Optional;

public interface RecipeInfoRepository extends Repository<RecipeInfo, RecipeId> {

    Optional<Long> findIdByName(String recipeName, UserId userId);

    boolean existsByIdAndUserId(RecipeId recipeId, UserId userId);

    // --- Abgleich mit dem recipe-Dienst ---

    /** Eintrag zu einem Rezept des recipe-Dienstes (auch wenn er gerade ausgeblendet ist). */
    Optional<Long> findIdBySourceId(long sourceRecipeId, UserId userId);

    /** Sichtbarer Eintrag dieses Namens ohne Bezug zum recipe-Dienst (eingetippt oder Altbestand). */
    Optional<Long> findUnlinkedIdByName(String recipeName, UserId userId);

    Optional<Long> findSourceId(RecipeId recipeId);

    void linkToSource(RecipeId recipeId, long sourceRecipeId);

    /** Haengt alle Mahlzeiten von {@code from} an {@code into} und entfernt {@code from}. */
    void mergeInto(RecipeId from, RecipeId into);

    /** Blendet den Eintrag aus, bis die naechste Meldung zu seinem Rezept kommt. */
    void hide(RecipeId recipeId);

    /** Geloescht, bleibt aber fuer bestehende Essensplaene erhalten. */
    void markDeleted(RecipeId recipeId);

    boolean isUsedInMealPlans(RecipeId recipeId);

}
