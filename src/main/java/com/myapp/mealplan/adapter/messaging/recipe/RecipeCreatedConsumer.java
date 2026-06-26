package com.myapp.mealplan.adapter.messaging.recipe;

import com.myapp.mealplan.adapter.RecipeInfoConverter;
import com.myapp.mealplan.application.MealPlanAccess;
import com.myapp.mealplan.application.RecipeInfoAccess;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;
import io.vertx.core.eventbus.MessageConsumer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
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

    @Incoming("recipe-info")
    public void consume(RecipeCreatedEvent event) {
        log.info("Received RecipeCreatedEvent: {} ({})", event.getId(), event.getName());

        event.setId(
                recipeInfoAccess
                        .getIdByName(event.getName(), event.getUserId())
                        .orElse(0L)
        );

        // Event -> Domain
        RecipeInfo recipeInfo = recipeInfoConverter.eventToDomain(event);


        recipeInfoAccess.save(recipeInfo);
    }
}
