package com.myapp.mealplan.application;


import com.my.common.api.AbstractAccess;
import com.my.common.api.UserId;
import com.my.common.api.pagination.PageRequest;
import com.my.common.api.pagination.PageResult;
import com.myapp.mealplan.domain.model.Meal;
import com.myapp.mealplan.domain.model.recipe.*;
import com.myapp.mealplan.domain.service.RecipeInfoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

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

    /**
     * Uebernimmt ein im recipe-Dienst angelegtes oder geaendertes Rezept.
     *
     * Wiedererkannt wird es an seiner ID im recipe-Dienst. Fuer Eintraege, die
     * diese ID noch nicht kennen (Altbestand, eingetippte Gerichte), zaehlt
     * ersatzweise der bisherige bzw. der aktuelle Name - so entsteht bei einer
     * Umbenennung kein zweiter Eintrag, und ein eingetipptes Gericht wird zum
     * Rezept, sobald es eines mit diesem Namen gibt.
     *
     * Laeuft in einer Transaktion und darf deshalb nicht vom IO-Thread aus
     * aufgerufen werden (der Consumer ist dafuer als blockierend markiert).
     *
     * @param sourceRecipeId ID des Rezepts im recipe-Dienst
     * @param previousName   Name vor der Aenderung oder null
     */
    @Transactional
    public RecipeInfo saveSyncedRecipe(long sourceRecipeId, String previousName, RecipeInfo incoming) {
        UserId userId = incoming.userId();
        String name = incoming.name();

        Optional<Long> sameName = repository.findIdByName(name, userId);
        Optional<Long> target = repository.findIdBySourceId(sourceRecipeId, userId);
        if (target.isEmpty() && previousName != null && !previousName.equals(name)) {
            target = repository.findUnlinkedIdByName(previousName, userId);
        }
        if (target.isEmpty()) {
            target = sameName;
        }

        if (target.isEmpty()) {
            RecipeInfo created = repository.save(withId(incoming, 0L));
            repository.linkToSource(created.id(), sourceRecipeId);
            return created;
        }

        RecipeId targetId = new RecipeId(target.get());
        if (sameName.isPresent() && !sameName.get().equals(target.get())) {
            makeNameAvailable(new RecipeId(sameName.get()), targetId);
        }
        RecipeInfo updated = repository.update(withId(incoming, targetId.id()));
        repository.linkToSource(targetId, sourceRecipeId);
        return updated;
    }

    /**
     * Ein anderer Eintrag traegt bereits den Namen, den {@code target} bekommen soll.
     */
    private void makeNameAvailable(RecipeId blocking, RecipeId target) {
        if (repository.findSourceId(blocking).isPresent()) {
            // Gehoert zu einem anderen Rezept des recipe-Dienstes. Dort sind Namen
            // je Nutzer eindeutig, dieses Rezept heisst also nicht mehr so - uns
            // fehlt nur seine Meldung. Ausblenden, bis sie kommt.
            repository.hide(blocking);
        } else {
            // Eingetipptes Gericht oder Altbestand gleichen Namens: geht im Rezept auf.
            repository.mergeInto(blocking, target);
        }
    }

    /**
     * Entfernt ein im recipe-Dienst geloeschtes Rezept. Steht es noch in
     * Essensplaenen, bleibt es fuer diese erhalten, wird aber nicht mehr
     * zur Auswahl angeboten.
     *
     * Laeuft in einer Transaktion, siehe {@link #saveSyncedRecipe}.
     */
    @Transactional
    public void removeSyncedRecipe(long sourceRecipeId, String name, UserId userId) {
        Optional<Long> target = repository.findIdBySourceId(sourceRecipeId, userId);
        if (target.isEmpty() && name != null) {
            target = repository.findUnlinkedIdByName(name, userId);
        }
        if (target.isEmpty()) {
            return;
        }
        RecipeId recipeId = new RecipeId(target.get());
        if (repository.isUsedInMealPlans(recipeId)) {
            repository.markDeleted(recipeId);
        } else {
            repository.deleteById(recipeId);
        }
    }

    private static RecipeInfo withId(RecipeInfo recipeInfo, long id) {
        return new RecipeInfo(recipeInfo.recipe().withId(new RecipeId(id)), recipeInfo.portions(), recipeInfo.recipeIngredients());
    }

    /** true, wenn es das Rezept gibt und es diesem Nutzer gehoert. */
    public boolean belongsToUser(RecipeId recipeId, UserId userId) {
        if (recipeId == null || userId == null || userId.value() == null) {
            return false;
        }
        return repository.existsByIdAndUserId(recipeId, userId);
    }

    @Override
    public PageResult<RecipeInfo> getAllByUser(UserId userId, PageRequest pageRequest) {
        return null;
    }
}
