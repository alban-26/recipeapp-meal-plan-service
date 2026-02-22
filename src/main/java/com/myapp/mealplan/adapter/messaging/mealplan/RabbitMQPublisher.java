package com.myapp.mealplan.adapter.messaging.mealplan;

import com.myapp.mealplan.adapter.MealPlanConverter;
import com.myapp.mealplan.domain.service.MessagePublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;


@ApplicationScoped
public class RabbitMQPublisher implements MessagePublisher {

    private final MealPlanConverter mealPlanConverter;

    @Inject
    @Channel("recipe-ingredients")
    Emitter<MealPlanCreatedEvent> emitter;

    @Inject
    public RabbitMQPublisher(MealPlanConverter mealPlanConverter) {
        this.mealPlanConverter = mealPlanConverter;
    }

    @Override
    public void publishMealPlanIngredients(MealPlanCreatedEvent event) {
        this.emitter.send(event);
    }

}
