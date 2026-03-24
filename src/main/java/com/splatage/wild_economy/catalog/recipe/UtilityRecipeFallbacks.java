package com.splatage.wild_economy.catalog.recipe;

import java.util.List;
import java.util.Map;

final class UtilityRecipeFallbacks {

    private UtilityRecipeFallbacks() {
    }

    static void apply(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        CommonCraftingRecipeNormalizers.apply(recipesByOutput);
    }
}
