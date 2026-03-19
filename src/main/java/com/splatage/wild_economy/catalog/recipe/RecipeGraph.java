package com.splatage.wild_economy.catalog.recipe;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class RecipeGraph {

    private final Map<String, List<RecipeDefinition>> recipesByOutputKey;

    public RecipeGraph(final Map<String, List<RecipeDefinition>> recipesByOutputKey) {
        this.recipesByOutputKey = Map.copyOf(recipesByOutputKey);
    }

    public List<RecipeDefinition> getRecipesFor(final String outputKey) {
        return this.recipesByOutputKey.getOrDefault(outputKey, Collections.emptyList());
    }

    public int recipeOutputCount() {
        return this.recipesByOutputKey.size();
    }
}
