package com.myapp.mealplan.adapter;

import com.my.common.api.UserId;
import com.myapp.mealplan.adapter.database.mealplan.DailyMealEntity;
import com.myapp.mealplan.adapter.database.mealplan.DailyMealPlanEntity;
import com.myapp.mealplan.adapter.database.mealplan.MealPlanEntity;
import com.myapp.mealplan.adapter.messaging.mealplan.MealPlanCreatedEvent;
import com.myapp.mealplan.application.RecipeInfoAccess;
import com.myapp.mealplan.domain.model.*;
import com.myapp.mealplan.domain.model.recipe.Recipe;
import com.myapp.mealplan.domain.model.recipe.RecipeId;
import com.myapp.mealplan.domain.model.recipe.RecipeIngredient;
import io.vertx.ext.auth.User;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.NonNull;
import org.openapitools.model.DailyMealPlanDto;
import org.openapitools.model.MealDto;
import org.openapitools.model.MealPlanDto;
import org.openapitools.model.RecipeDto;
import org.apache.commons.lang3.NotImplementedException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MealPlanConverterImpl implements MealPlanConverter {
    @Override
    public MealPlan entityToDomain(@NonNull MealPlanEntity mealPlanEntity) {
        return new MealPlan(new MealPlanId(mealPlanEntity.getId()), mealPlanEntity.getStartDate(), mealPlanEntity.getEndDate(), mealPlanEntity.getDailyMealPlans().stream().map(dailyMealPlan -> this.createDailyMealPlan(dailyMealPlan, new UserId(mealPlanEntity.getUserId()))).toList(), new UserId(mealPlanEntity.getUserId()));
    }


    DailyMealPlan createDailyMealPlan(DailyMealPlanEntity entity, UserId userId) {
        return new DailyMealPlan(new DailyMealPlanId(entity.getId()), entity.getDate(), toMeals(entity.getDailyMeals(), userId));
    }

    Map.Entry<Mealtime, Meal> createDailyMeals(DailyMealEntity dailyMealEntity, UserId userId) {
        // Parse Mealtime enum from stored string
        Mealtime mealtime = Mealtime.valueOf(dailyMealEntity.getMealTime().toUpperCase());

        // Build RecipeInfo
        Recipe recipe =
                new Recipe(new RecipeId(dailyMealEntity.getRecipeId()), // assuming RecipeId wraps Long
                        dailyMealEntity.getRecipeName(), userId);

        // Build Meal
        Meal meal = new Meal(recipe, dailyMealEntity.getServings());

        // Return entry
        return new AbstractMap.SimpleEntry<>(mealtime, meal);
    }


    public EnumMap<Mealtime, List<Meal>> toMeals(List<DailyMealEntity> dailyMeals, UserId userId) {
        return dailyMeals.stream()
                .map(dailyMeal -> this.createDailyMeals(dailyMeal, userId)) // Map.Entry<Mealtime, Meal>
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        () -> new EnumMap<>(Mealtime.class),
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }



    @Override
    public MealPlanEntity domainToEntity(@NonNull MealPlan mealPlan) {

        MealPlanEntity entity = new MealPlanEntity();
        entity.setId(mealPlan.mealPlanId() != null ? mealPlan.mealPlanId().id() : 0);
        entity.setStartDate(mealPlan.startDate());
        entity.setEndDate(mealPlan.endDate());
        entity.setUserId(mealPlan.userId().value()); // TODO HIER NULL WAS LOS?

        if (mealPlan.dailyMealPlans() != null) {
            List<DailyMealPlanEntity> dailyMealPlanEntities = mealPlan.dailyMealPlans().stream()
                    .map(dmp -> mapDailyMealPlan(dmp, entity.getId()))
                    .toList();

            entity.setDailyMealPlans(dailyMealPlanEntities);
        }

        return entity;
    }

    public DailyMealPlanEntity mapDailyMealPlan(@NonNull DailyMealPlan dailyMealPlan, @NonNull Long mealPlanId) {
        DailyMealPlanEntity entity = new DailyMealPlanEntity();
        entity.setId(dailyMealPlan.id() != null ? dailyMealPlan.id().id() : 0);
        entity.setMealPlanId(mealPlanId);
        entity.setDate(dailyMealPlan.date());

        if (dailyMealPlan.meals() != null) {
            List<DailyMealEntity> dailyMealEntities = dailyMealPlan.meals().entrySet().stream()
                    .flatMap(entry -> {
                        Mealtime mealtime = entry.getKey();
                        List<Meal> meals = entry.getValue();

                        return meals.stream().map(meal -> mapMeal(meal, entity.getId(), mealtime));
                    })
                    .toList();

            entity.setDailyMeals(dailyMealEntities);
        }

        return entity;
    }

    public DailyMealPlan mapToDailyMealPlan(@NonNull DailyMealPlanEntity entity) {

        DailyMealPlanId id = new DailyMealPlanId(entity.getId());
        LocalDate date = entity.getDate();

        EnumMap<Mealtime, List<Meal>> mealsMap = new EnumMap<>(Mealtime.class);

        if (entity.getDailyMeals() != null) {
            for (DailyMealEntity mealEntity : entity.getDailyMeals()) {
                Mealtime mealtime = Mealtime.valueOf(mealEntity.getMealTime());

                Meal meal = mapToMeal(mealEntity);
                mealsMap
                        .computeIfAbsent(mealtime, k -> new ArrayList<>())
                        .add(meal);
            }
        }

        return new DailyMealPlan(id, date, mealsMap);
    }

    private Meal mapToMeal(DailyMealEntity entity) {
        Recipe recipe = new Recipe(
                new RecipeId(entity.getRecipeId()),
                entity.getRecipeName(),
                null
        );

        return new Meal(recipe, entity.getServings());
    }


    private DailyMealEntity mapMeal(Meal meal, Long dailyMealPlanId, Mealtime mealtime) {
        DailyMealEntity entity = new DailyMealEntity();
        entity.setDailyMealPlanId(dailyMealPlanId);
        entity.setRecipeId(meal.recipe().id().id());
        entity.setRecipeName(meal.recipe().name());
        entity.setMealTime(mealtime.name());
        entity.setServings(meal.servings());
        return entity;
    }


    @Override
    public MealPlan eventToDomain(MealPlanCreatedEvent mealPlanCreatedEvent) {
        throw new UnsupportedOperationException(
                "Creating MealPlan from MealPlanCreatedEvent is not supported by design."
        );
    }

    @Override
    public MealPlanCreatedEvent domainToEvent(MealPlan mealPlan) {
        throw new UnsupportedOperationException(
                "Creating MealPlan from MealPlanCreatedEvent is not supported by design."
        );
    }

    @Override
    public MealPlanCreatedEvent domainToEvent(@NonNull MealPlan mealPlan, @NonNull RecipeInfoAccess recipeInfoAccess) {
        Collection<Meal> meals = getMeals(mealPlan);
        Collection<RecipeIngredient> recipeIngredients = recipeInfoAccess.getIngredientsByMeals(meals);
        return new MealPlanCreatedEvent(recipeIngredients, mealPlan.userId());
    }


    private static Collection<Meal> getMeals(MealPlan mealPlan) {
        return mealPlan.dailyMealPlans().stream()
                .flatMap(daily -> daily.meals().values().stream())
                .flatMap(Collection::stream)
                .toList();
    }


    @Override
    public MealPlan dtoToDomain(@NonNull MealPlanDto dto) {

        List<DailyMealPlan> dailyMealPlans = Optional.ofNullable(dto.getDailyMealPlans())
                .orElse(List.of())
                .stream()
                .map(this::dtoToDomain)
                .collect(Collectors.toList());

        return new MealPlan(
                new MealPlanId(dto.getId() != null ? dto.getId() : 0L),
                dto.getStartDate(),
                dto.getEndDate(),
                dailyMealPlans,
                null);
    }

    public DailyMealPlan dtoToDomain(@NonNull DailyMealPlanDto dto) {
        EnumMap<Mealtime, List<Meal>> meals = new EnumMap<>(Mealtime.class);
        if (dto.getMeals() != null) {
            dto.getMeals().forEach((mealtime, mealDtos) -> {
                List<Meal> domainMeals = mealDtos.stream()
                        .map(this::dtoToDomain)
                        .collect(Collectors.toList());
                meals.put(Mealtime.valueOf(mealtime), domainMeals);
            });
        }

        return new DailyMealPlan(
                new DailyMealPlanId(dto.getId() != null ? dto.getId() : 0L),
                dto.getDate(),
                meals
        );
    }

    private Meal dtoToDomain(MealDto dto) {
        return new Meal(
                dtoToDomain(dto.getRecipe()),
                dto.getServings()
        );
    }

    private Recipe dtoToDomain(RecipeDto dto) {
        return new Recipe(
                new RecipeId(dto.getId() != null ? dto.getId() : 0L),
                dto.getName(),
                null
        );
    }

    @Override
    public MealPlanDto domainToDto(@NonNull MealPlan domain) {

        List<DailyMealPlanDto> dailyMealPlans = Optional.ofNullable(domain.dailyMealPlans())
                .orElse(List.of())
                .stream()
                .map(this::domainToDto)
                .collect(Collectors.toList());

        return new MealPlanDto(
                domain.mealPlanId() != null ? domain.mealPlanId().id() : null,
                domain.startDate(),
                domain.endDate(),
                dailyMealPlans
        );
    }

    public DailyMealPlanDto domainToDto(@NonNull DailyMealPlan domain) {
        Map<String, List<MealDto>> meals = new LinkedHashMap<>();
        if (domain.meals() != null) {
            domain.meals().forEach((mealtime, mealList) -> {
                List<MealDto> mealDtos = mealList.stream()
                        .map(this::domainToDto)
                        .collect(Collectors.toList());
                meals.put(mealtime.name(), mealDtos);
            });
        }

        return new DailyMealPlanDto(
                domain.id() != null ? domain.id().id() : null,
                domain.date(),
                meals
        );
    }

    public static LocalDate convertDateToLocalDate(Date date) {
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }


    private MealDto domainToDto(Meal domain) {
        return new MealDto(
                domainToDto(domain.recipe()),
                domain.servings()
        );
    }

    private RecipeDto domainToDto(Recipe domain) {
        return new RecipeDto(
                domain.id() != null ? domain.id().id() : null,
                domain.name()
        );
    }
}
