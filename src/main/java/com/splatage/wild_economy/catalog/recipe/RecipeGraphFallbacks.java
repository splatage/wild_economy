package com.splatage.wild_economy.catalog.recipe;

import java.util.List;
import java.util.Map;

public final class RecipeGraphFallbacks {

    private RecipeGraphFallbacks() {
    }

    public static void apply(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        WoodFamilyRecipeFallbacks.apply(recipesByOutput);
        UtilityRecipeFallbacks.apply(recipesByOutput);
    }
}
