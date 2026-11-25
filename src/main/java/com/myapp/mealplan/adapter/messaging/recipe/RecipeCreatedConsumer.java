package com.myapp.mealplan.adapter.messaging.recipe;

import com.myapp.mealplan.adapter.RecipeInfoConverter;
import com.myapp.mealplan.application.MealPlanAccess;
import com.myapp.mealplan.application.RecipeInfoAccess;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;
import io.vertx.core.eventbus.MessageConsumer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import io.smallrye.reactive.messaging.annotations.Blocking;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Optional;

@ApplicationScoped
@Slf4j
public class RecipeCreatedConsumer {

    private final MealPlanAccess mealPlanAccess;

    private final RecipeInfoAccess recipeInfoAccess;

    private final RecipeInfoConverter recipeInfoConverter;

    @Inject
    public RecipeCreatedConsumer(MealPlanAccess mealPlanService, RecipeInfoAccess recipeInfoAccess, RecipeInfoConverter recipeInfoConverter) {
        this.mealPlanAccess = mealPlanService;
        this.recipeInfoAccess = recipeInfoAccess;
        this.recipeInfoConverter = recipeInfoConverter;
    }

    /**
     * Blockierend, weil hier in die Datenbank geschrieben wird und der Abgleich
     * in einer Transaktion laeuft (auf dem IO-Thread nicht erlaubt). Die
     * Reihenfolge der Meldungen bleibt erhalten.
     */
    @Incoming("recipe-info")
    @Blocking
    public void consume(RecipeCreatedEvent event) {
        log.info("Received RecipeCreatedEvent: {} ({}){}", event.getId(), event.getName(), event.isDeleted() ? " [deleted]" : "");

        if (event.isDeleted()) {
            recipeInfoAccess.removeSyncedRecipe(event.getId(), event.getName(), event.getUserId());
            return;
        }

        // Event -> Domain. Die ID im Event ist die des recipe-Dienstes; welcher
        // Eintrag hier dazu gehoert, entscheidet saveSyncedRecipe.
        RecipeInfo recipeInfo = recipeInfoConverter.eventToDomain(event);
        recipeInfoAccess.saveSyncedRecipe(event.getId(), event.getPreviousName(), recipeInfo);
    }
}
