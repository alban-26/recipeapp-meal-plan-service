package com.myapp.mealplan.adapter.messaging.recipe;


import com.my.common.api.UserId;
import com.myapp.mealplan.domain.model.recipe.RecipeIngredient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeCreatedEvent {

    private Long id;
    private String name;
    private Integer portions;
    private List<RecipeIngredient> ingredients;
    private UserId userId;

    /**
     * Name vor der Aenderung. Fehlt bei neuen Rezepten und bei Meldungen eines
     * aelteren recipe-Dienstes.
     */
    private String previousName;

    /**
     * true: das Rezept wurde im recipe-Dienst geloescht. Fehlt bei Meldungen
     * eines aelteren recipe-Dienstes (dann false).
     */
    private boolean deleted;

}
