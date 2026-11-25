package com.myapp.mealplan.adapter.restapi;

import com.my.common.api.UserId;
import com.myapp.mealplan.adapter.MealPlanConverter;
import com.myapp.mealplan.adapter.RecipeInfoConverter;
import com.myapp.mealplan.application.MealPlanAccess;
import com.myapp.mealplan.application.RecipeInfoAccess;
import com.myapp.mealplan.domain.model.DailyMealPlan;
import com.myapp.mealplan.domain.model.DailyMealPlanId;
import com.myapp.mealplan.domain.model.Meal;
import com.myapp.mealplan.domain.model.MealPlan;
import com.myapp.mealplan.domain.model.MealPlanId;
import com.myapp.mealplan.domain.model.recipe.Recipe;
import com.myapp.mealplan.domain.model.recipe.RecipeId;
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
import java.util.Collection;
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

        // Anlegen heisst immer "neu": eine mitgeschickte ID wird ignoriert, sonst
        // wuerde hier ein bestehender (auch fremder) Plan ueberschrieben.
        MealPlan mealPlan = mealPlanConverter.dtoToDomain(mealPlanDto)
                .withMealPlanId(new MealPlanId(0L))
                .withUserId(currentUserId);
        requireOwnRecipes(mealPlan.dailyMealPlans(), currentUserId);

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

        MealPlan mealPlan = requireOwnMealPlan(id, UserId.of(jwt.getSubject()));

        log.info("Meal plan retrieved successfully with id={}", id);
        return Response.ok(mealPlanConverter.domainToDto(mealPlan)).build();
    }

    @Override
    public Response updateMealPlan(Long id, MealPlanDto mealPlanDto) {
        UserId currentUserId = UserId.of(jwt.getSubject());
        log.info("Updating meal plan with id={}", id);

        requireOwnMealPlan(id, currentUserId);

        // Massgeblich ist die ID aus dem Pfad, nicht die aus dem Body: geprueft
        // wurde der Plan aus dem Pfad, also wird auch genau dieser geaendert.
        MealPlan mealPlan = mealPlanConverter.dtoToDomain(mealPlanDto)
                .withMealPlanId(new MealPlanId(id))
                .withUserId(currentUserId);
        requireOwnRecipes(mealPlan.dailyMealPlans(), currentUserId);

        MealPlan updated = mealPlanAccess.save(mealPlan);
        log.info("Meal plan updated successfully with id={}", id);

        return Response.ok(mealPlanConverter.domainToDto(updated)).build();
    }

    @Override
    public Response updateDailyMealPlan(Long mealPlanId, Long dailyMealPlanId, DailyMealPlanDto dailyMealPlanDto) {
        log.info("Updating daily meal plan id={} in meal plan id={}", dailyMealPlanId, mealPlanId);

        UserId currentUserId = UserId.of(jwt.getSubject());
        MealPlan mealPlan = requireOwnMealPlan(mealPlanId, currentUserId);
        // Der Tag muss wirklich zu diesem Plan gehoeren - geaendert wird sonst
        // allein anhand der Tages-ID, also auch ein Tag aus einem fremden Plan.
        requireDailyMealPlan(mealPlan, dailyMealPlanId);

        DailyMealPlan daily = mealPlanConverter.dtoToDomain(dailyMealPlanDto)
                .withId(new DailyMealPlanId(dailyMealPlanId));
        requireOwnRecipes(List.of(daily), currentUserId);

        DailyMealPlan updated = mealPlanAccess.updateDailyMealPlan(daily, new MealPlanId(mealPlanId));

        log.info("Daily meal plan updated successfully id={} in meal plan id={}", dailyMealPlanId, mealPlanId);
        return Response.ok(mealPlanConverter.domainToDto(updated)).build();
    }

    @Override
    public Response deleteDailyMealPlan(Long mealPlanId, Long dailyMealPlanId) {
        log.info("Deleting daily meal plan id={} from meal plan id={}", dailyMealPlanId, mealPlanId);

        MealPlan mealPlan = requireOwnMealPlan(mealPlanId, UserId.of(jwt.getSubject()));
        DailyMealPlan dailyToDelete = requireDailyMealPlan(mealPlan, dailyMealPlanId);

        mealPlanAccess.deleteDailyMealPlan(dailyToDelete);
        log.info("Successfully deleted daily meal plan id={} from meal plan id={}", dailyMealPlanId, mealPlanId);

        return Response.noContent().build();
    }

    @Override
    public Response deleteMealPlan(Long id) {
        MealPlan mealPlan = requireOwnMealPlan(id, UserId.of(jwt.getSubject()));
        mealPlanAccess.delete(mealPlan);
        log.info("Successfully deleted meal plan id={}", id);
        return Response.noContent().build();
    }

    @Override
    public Response generateShoppingList(MealPlanDto mealPlanDto) {
        UserId currentUserId = UserId.of(jwt.getSubject());
        log.info("User {} is generating a shopping list for meal plan with id: {}", currentUserId.value(), mealPlanDto.getId());

        MealPlan mealPlan = mealPlanConverter.dtoToDomain(mealPlanDto).withUserId(currentUserId);
        // Die Einkaufsliste entsteht aus den Zutaten der genannten Rezepte -
        // also duerfen nur eigene Rezepte genannt sein.
        requireOwnRecipes(mealPlan.dailyMealPlans(), currentUserId);
        mealPlanAccess.generateShoppingList(mealPlan);
        return Response.ok().build();
    }

    /**
     * Liefert den Plan nur, wenn er dem angemeldeten Nutzer gehoert. Ein
     * fremder Plan wird genauso behandelt wie ein nicht vorhandener (404),
     * damit sich ueber die Antwort nicht herausfinden laesst, welche IDs es gibt.
     */
    private MealPlan requireOwnMealPlan(Long id, UserId currentUserId) {
        return mealPlanAccess.getById(new MealPlanId(id))
                .filter(mealPlan -> isOwner(mealPlan, currentUserId))
                .orElseThrow(() -> {
                    log.warn("Meal plan not found with id={}", id);
                    return new jakarta.ws.rs.NotFoundException("Meal plan not found with id: " + id);
                });
    }

    private static boolean isOwner(MealPlan mealPlan, UserId currentUserId) {
        String owner = mealPlan.userId() != null ? mealPlan.userId().value() : null;
        String current = currentUserId != null ? currentUserId.value() : null;
        return owner != null && owner.equals(current);
    }

    private static DailyMealPlan requireDailyMealPlan(MealPlan mealPlan, Long dailyMealPlanId) {
        return mealPlan.dailyMealPlans().stream()
                .filter(d -> d.id().equals(new DailyMealPlanId(dailyMealPlanId)))
                .findFirst()
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException(
                        "DailyMealPlan not found: " + dailyMealPlanId + " in MealPlan " + mealPlan.mealPlanId().id()));
    }

    /**
     * Rezepte, die ein Plan ueber ihre ID nennt, muessen dem angemeldeten
     * Nutzer gehoeren. Sonst liessen sich fremde Rezepte in den eigenen Plan
     * haengen und ueber Plan und Einkaufsliste auslesen. Frei eingetippte
     * Gerichte (ID 0) sind keine Verweise und bleiben erlaubt.
     */
    private void requireOwnRecipes(Collection<DailyMealPlan> dailyMealPlans, UserId currentUserId) {
        if (dailyMealPlans == null) {
            return;
        }
        dailyMealPlans.stream()
                .filter(daily -> daily.meals() != null)
                .flatMap(daily -> daily.meals().values().stream())
                .flatMap(List::stream)
                .map(Meal::recipe)
                .filter(recipe -> recipe != null && recipe.id() != null && recipe.id().id() != 0)
                .map(Recipe::id)
                .distinct()
                .forEach(recipeId -> requireOwnRecipe(recipeId, currentUserId));
    }

    private void requireOwnRecipe(RecipeId recipeId, UserId currentUserId) {
        if (!recipeInfoAccess.belongsToUser(recipeId, currentUserId)) {
            log.warn("Recipe not found with id={}", recipeId.id());
            throw new jakarta.ws.rs.NotFoundException("Recipe not found with id: " + recipeId.id());
        }
    }
}
