package com.splatage.wild_economy.catalog.recipe;

import java.util.List;

public record RecipeDefinition(
    String outputKey,
    int outputAmount,
    String recipeType,
    List<RecipeIngredient> ingredients
) {
}
