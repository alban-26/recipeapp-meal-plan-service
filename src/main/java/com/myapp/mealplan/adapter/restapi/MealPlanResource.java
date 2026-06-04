package com.myapp.mealplan.adapter.restapi;

import com.my.common.api.UserId;
import com.myapp.mealplan.adapter.MealPlanConverter;
import com.myapp.mealplan.adapter.RecipeInfoConverter;
import com.myapp.mealplan.application.MealPlanAccess;
import com.myapp.mealplan.application.RecipeInfoAccess;
import com.myapp.mealplan.domain.model.DailyMealPlan;
import com.myapp.mealplan.domain.model.DailyMealPlanId;
import com.myapp.mealplan.domain.model.MealPlan;
import com.myapp.mealplan.domain.model.MealPlanId;
import com.myapp.mealplan.domain.model.recipe.RecipeInfo;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.NonBlocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.openapitools.api.MealPlansApi;
import org.openapitools.model.DailyMealPlanDto;
import org.openapitools.model.MealPlanDto;
import org.openapitools.model.RecipeDto;

import java.net.URI;
import java.util.List;

@Path("/mealPlans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NonBlocking
@Slf4j
@ApplicationScoped
public class MealPlanResource implements MealPlansApi {

    private final MealPlanAccess mealPlanAccess;
    private final RecipeInfoAccess recipeInfoAccess;
    private final MealPlanConverter mealPlanConverter;
    private final RecipeInfoConverter recipeInfoConverter;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    JsonWebToken jwt;

    @Inject
    public MealPlanResource(MealPlanAccess mealPlanAccess, RecipeInfoAccess recipeInfoAccess, MealPlanConverter mealPlanConverter, RecipeInfoConverter recipeInfoConverter) {
        this.mealPlanAccess = mealPlanAccess;
        this.recipeInfoAccess = recipeInfoAccess;
        this.mealPlanConverter = mealPlanConverter;
        this.recipeInfoConverter = recipeInfoConverter;
    }

    @Override
    public Response createMealPlan(MealPlanDto mealPlanDto, Boolean generateShoppingList) {
        UserId currentUserId = UserId.of(jwt.getSubject());
        log.info("User {} is creating a new meal plan: {}", currentUserId.value(), mealPlanDto.getId());

        MealPlan mealPlan = mealPlanConverter.dtoToDomain(mealPlanDto).withUserId(currentUserId);

        MealPlan saved = generateShoppingList
                ? mealPlanAccess.saveAndGenerateShoppingList(mealPlan)
                : mealPlanAccess.save(mealPlan);

        log.info("Meal plan created successfully with id={} by user={}", saved.mealPlanId().id(), currentUserId.value());

        URI location = URI.create("/mealPlans/" + saved.mealPlanId().id());
        return Response.created(location)
                .entity(mealPlanConverter.domainToDto(saved))
                .build();
    }

    @Override
    public Response getMealPlans() {
        log.info("Fetching all meal plans");
        UserId currentUserId = UserId.of(jwt.getSubject());
        List<MealPlan> mealPlans = mealPlanAccess.getAllByUser(currentUserId);

        if (mealPlans.isEmpty()) {
            log.info("No meal plans found");
            return Response.noContent().build();
        }

        log.info("Found {} meal plans", mealPlans.size());
        List<MealPlanDto> dtos = mealPlans.stream()
                .map(mealPlanConverter::domainToDto)
                .toList();

        return Response.ok(dtos).build();
    }

    @Override
    public Response getRecipes() {
        log.debug("Fetching all recipes");
        UserId currentUserId = UserId.of(jwt.getSubject());
        List<RecipeInfo> recipeInfos = recipeInfoAccess.getAllByUser(currentUserId);

        log.info("Found {} recipes", recipeInfos.size());
        List<RecipeDto> dtos = recipeInfos.stream()
                .map(recipe -> this.recipeInfoConverter.domainToDto(recipe.recipe()))
                .toList();

        return Response.ok(dtos).build();
    }

    @Override
    public Response getMealPlanById(Long id) {
        log.info("Fetching meal plan by id={}", id);

        MealPlan mealPlan = mealPlanAccess.getById(new MealPlanId(id))
                .orElseThrow(() -> {
                    log.warn("Meal plan not found with id={}", id);
                    return new jakarta.ws.rs.NotFoundException("Meal plan not found with id: " + id);
                });

        log.info("Meal plan retrieved successfully with id={}", id);
        return Response.ok(mealPlanConverter.domainToDto(mealPlan)).build();
    }

    @Override
    public Response updateMealPlan(Long id, MealPlanDto mealPlanDto) {
        UserId currentUserId = UserId.of(jwt.getSubject());
        log.info("Updating meal plan with id={}", id);

        MealPlan updated = mealPlanAccess.save(mealPlanConverter.dtoToDomain(mealPlanDto).withUserId(currentUserId));
        log.info("Meal plan updated successfully with id={}", id);

        return Response.ok(mealPlanConverter.domainToDto(updated)).build();
    }

    @Override
    public Response updateDailyMealPlan(Long mealPlanId, Long dailyMealPlanId, DailyMealPlanDto dailyMealPlanDto) {
        log.info("Updating daily meal plan id={} in meal plan id={}", dailyMealPlanId, mealPlanId);

        DailyMealPlan daily = mealPlanConverter.dtoToDomain(dailyMealPlanDto)
                .withId(new DailyMealPlanId(dailyMealPlanId));

        DailyMealPlan updated = mealPlanAccess.updateDailyMealPlan(daily, new MealPlanId(mealPlanId));

        log.info("Daily meal plan updated successfully id={} in meal plan id={}", dailyMealPlanId, mealPlanId);
        return Response.ok(mealPlanConverter.domainToDto(updated)).build();
    }

    @Override
    public Response deleteDailyMealPlan(Long mealPlanId, Long dailyMealPlanId) {
        log.info("Deleting daily meal plan id={} from meal plan id={}", dailyMealPlanId, mealPlanId);

        MealPlan mealPlan = mealPlanAccess.getById(new MealPlanId(mealPlanId))
                .orElseThrow(() -> {
                    log.warn("MealPlan not found: {}", mealPlanId);
                    return new jakarta.ws.rs.NotFoundException("MealPlan not found: " + mealPlanId);
                });

        DailyMealPlan dailyToDelete = mealPlan.dailyMealPlans().stream()
                .filter(d -> d.id().equals(new DailyMealPlanId(dailyMealPlanId)))
                .findFirst()
                .orElseThrow(() -> {
                    return new jakarta.ws.rs.NotFoundException(
                            "DailyMealPlan not found: " + dailyMealPlanId + " in MealPlan " + mealPlanId);
                });

        mealPlanAccess.deleteDailyMealPlan(dailyToDelete);
        log.info("Successfully deleted daily meal plan id={} from meal plan id={}", dailyMealPlanId, mealPlanId);

        return Response.noContent().build();
    }

    @Override
    public Response deleteMealPlan(Long id) {
        MealPlan mealPlan = mealPlanAccess.getById(new MealPlanId(id))
                .orElseThrow(() -> {
                    return new jakarta.ws.rs.NotFoundException("Meal plan not found with id: " + id);
                });
        mealPlanAccess.delete(mealPlan);
        log.info("Successfully deleted meal plan id={}", id);
        return Response.noContent().build();
    }

    @Override
    public Response generateShoppingList(MealPlanDto mealPlanDto) {
        UserId currentUserId = UserId.of(jwt.getSubject());
        log.info("User {} is generating a shopping list for meal plan with id: {}", currentUserId.value(), mealPlanDto.getId());

        MealPlan mealPlan = mealPlanConverter.dtoToDomain(mealPlanDto).withUserId(currentUserId);
        mealPlanAccess.generateShoppingList(mealPlan);
        return Response.ok().build();
    }
}
