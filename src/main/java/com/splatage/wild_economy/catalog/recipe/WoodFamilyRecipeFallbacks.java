package com.splatage.wild_economy.catalog.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

public final class WoodFamilyRecipeFallbacks {

    private WoodFamilyRecipeFallbacks() {
    }

    public static void apply(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        for (final WoodLikeRecipeFamily family : WoodLikeRecipeFamilies.all()) {
            applyPlanksFromInputUnit(recipesByOutput, family);
            applyWoodFromInputUnits(recipesByOutput, family);
            applyStrippedInputFromInputUnit(recipesByOutput, family);
            applyWoodenSign(recipesByOutput, family);
            applyHangingSign(recipesByOutput, family);
            applyStairs(recipesByOutput, family);
            applyBoatLike(recipesByOutput, family);
            applyChestBoatLike(recipesByOutput, family);
        }
        applyBambooMosaicStairs(recipesByOutput);
    }

    private static void applyPlanksFromInputUnit(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodLikeRecipeFamily family
    ) {
        if (!hasMaterial(family.inputUnitKey()) || !hasMaterial(family.planksKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.planksKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.planksKey(),
                family.planksOutputAmount(),
                "fallback_planks_from_input_unit",
                List.of(new RecipeIngredient(family.inputUnitKey(), 1))
            )
        );
    }

    private static void applyWoodFromInputUnits(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodLikeRecipeFamily family
    ) {
        if (family.woodLikeOutputKey() == null) {
            return;
        }
        if (!hasMaterial(family.inputUnitKey()) || !hasMaterial(family.woodLikeOutputKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.woodLikeOutputKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.woodLikeOutputKey(),
                family.woodLikeOutputAmount(),
                "fallback_wood_from_input_units",
                List.of(new RecipeIngredient(family.inputUnitKey(), 4))
            )
        );
    }

    private static void applyStrippedInputFromInputUnit(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodLikeRecipeFamily family
    ) {
        if (family.strippedInputUnitKey() == null) {
            return;
        }
        if (!hasMaterial(family.inputUnitKey()) || !hasMaterial(family.strippedInputUnitKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.strippedInputUnitKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.strippedInputUnitKey(),
                1,
                "fallback_stripped_input_from_input_unit",
                List.of(new RecipeIngredient(family.inputUnitKey(), 1))
            )
        );
    }

    private static void applyWoodenSign(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodLikeRecipeFamily family
    ) {
        if (!hasMaterial(family.planksKey()) || !hasMaterial(family.signKey()) || !hasMaterial("stick")) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.signKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.signKey(),
                3,
                "fallback_wooden_sign",
                List.of(
                    new RecipeIngredient(family.planksKey(), 6),
                    new RecipeIngredient("stick", 1)
                )
            )
        );
    }

    private static void applyHangingSign(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodLikeRecipeFamily family
    ) {
        if (family.strippedInputUnitKey() == null) {
            return;
        }
        if (!hasMaterial(family.strippedInputUnitKey()) || !hasMaterial(family.hangingSignKey())) {
            return;
        }
        if (!hasMaterial("iron_ingot")) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.hangingSignKey(),
                6,
                "fallback_hanging_sign_from_stripped_input_and_iron",
                List.of(
                    new RecipeIngredient(family.strippedInputUnitKey(), 6),
                    new RecipeIngredient("iron_ingot", 3)
                )
            )
        );
    }

    private static void applyStairs(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodLikeRecipeFamily family
    ) {
        if (!hasMaterial(family.planksKey()) || !hasMaterial(family.stairsKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.stairsKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.stairsKey(),
                4,
                "fallback_wood_like_stairs",
                List.of(new RecipeIngredient(family.planksKey(), 6))
            )
        );
    }

    private static void applyBoatLike(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodLikeRecipeFamily family
    ) {
        if (!family.supportsBoatRecipes()) {
            return;
        }
        if (!hasMaterial(family.planksKey()) || !hasMaterial(family.boatLikeKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.boatLikeKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.boatLikeKey(),
                1,
                "fallback_boat_like",
                List.of(new RecipeIngredient(family.planksKey(), 5))
            )
        );
    }

    private static void applyChestBoatLike(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodLikeRecipeFamily family
    ) {
        if (!family.supportsBoatRecipes()) {
            return;
        }
        if (!hasMaterial(family.planksKey()) || !hasMaterial(family.chestBoatLikeKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.chestBoatLikeKey(),
                1,
                "fallback_chest_boat_like_from_planks",
                List.of(new RecipeIngredient(family.planksKey(), 13))
            )
        );
    }

    private static void applyBambooMosaicStairs(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("bamboo_mosaic") || !hasMaterial("bamboo_mosaic_stairs")) {
            return;
        }
        if (hasRecipes(recipesByOutput, "bamboo_mosaic_stairs")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "bamboo_mosaic_stairs",
                4,
                "fallback_bamboo_mosaic_stairs",
                List.of(new RecipeIngredient("bamboo_mosaic", 6))
            )
        );
    }

    private static boolean hasRecipes(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final String outputKey
    ) {
        final List<RecipeDefinition> definitions = recipesByOutput.get(outputKey);
        return definitions != null && !definitions.isEmpty();
    }

    private static void addRecipe(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final RecipeDefinition recipeDefinition
    ) {
        recipesByOutput.computeIfAbsent(recipeDefinition.outputKey(), ignored -> new ArrayList<>())
            .add(recipeDefinition);
    }

    private static boolean hasMaterial(final String itemKey) {
        final String enumName = itemKey.toUpperCase(Locale.ROOT);
        return Material.matchMaterial(enumName) != null;
    }
}
