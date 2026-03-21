# wild_economy chest boat + hanging sign hotfix (`89e89777fe7358fb1f64edfaebe777231573e1c6`)

This bundle contains the complete replacement file for the remaining wood-family derivation hotfix.

Scope of this hotfix:
- keep the Phase 1 admin/catalog pipeline unchanged
- fix the remaining chest boat derivation gap
- fix the remaining hanging sign derivation gap
- make both fallback recipes participate even when Bukkit already exposes a recipe for the same output

---

## File: `src/main/java/com/splatage/wild_economy/catalog/recipe/WoodFamilyRecipeFallbacks.java`

```java
package com.splatage.wild_economy.catalog.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

public final class WoodFamilyRecipeFallbacks {

    private static final List<WoodFamily> WOOD_FAMILIES = List.of(
        WoodFamily.normal("oak"),
        WoodFamily.normal("spruce"),
        WoodFamily.normal("birch"),
        WoodFamily.normal("jungle"),
        WoodFamily.normal("acacia"),
        WoodFamily.normal("dark_oak"),
        WoodFamily.normal("mangrove"),
        WoodFamily.normal("cherry"),
        WoodFamily.normal("pale_oak"),
        WoodFamily.nether("crimson"),
        WoodFamily.nether("warped")
    );

    private WoodFamilyRecipeFallbacks() {
    }

    public static void apply(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        for (final WoodFamily family : WOOD_FAMILIES) {
            applyPlanksFromLog(recipesByOutput, family);
            applyWoodFromLogs(recipesByOutput, family);
            applyStrippedInputFromLog(recipesByOutput, family);
            applyWoodenSign(recipesByOutput, family);
            applyHangingSign(recipesByOutput, family);
            applyStairs(recipesByOutput, family);
            applyBoat(recipesByOutput, family);
            applyChestBoat(recipesByOutput, family);
        }
    }

    private static void applyPlanksFromLog(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!hasMaterial(family.inputLogKey()) || !hasMaterial(family.planksKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.planksKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.planksKey(),
                4,
                "fallback_planks_from_log",
                List.of(new RecipeIngredient(family.inputLogKey(), 1))
            )
        );
    }

    private static void applyWoodFromLogs(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!hasMaterial(family.inputLogKey()) || !hasMaterial(family.woodLikeOutputKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.woodLikeOutputKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.woodLikeOutputKey(),
                3,
                "fallback_wood_from_logs",
                List.of(new RecipeIngredient(family.inputLogKey(), 4))
            )
        );
    }

    private static void applyStrippedInputFromLog(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!hasMaterial(family.inputLogKey()) || !hasMaterial(family.strippedInputLogKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.strippedInputLogKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.strippedInputLogKey(),
                1,
                "fallback_stripped_input_from_log",
                List.of(new RecipeIngredient(family.inputLogKey(), 1))
            )
        );
    }

    private static void applyWoodenSign(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
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
        final WoodFamily family
    ) {
        if (!hasMaterial(family.strippedInputLogKey()) || !hasMaterial(family.hangingSignKey())) {
            return;
        }
        if (!hasMaterial("iron_ingot")) {
            return;
        }

        // Always add this fallback even if Bukkit already exposes a hanging-sign recipe.
        // The built-in path can remain blocked when chain derivation is unresolved; this
        // direct cost fallback gives the derivation engine a self-contained rooted path.
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.hangingSignKey(),
                6,
                "fallback_hanging_sign_from_stripped_logs_and_iron",
                List.of(
                    new RecipeIngredient(family.strippedInputLogKey(), 6),
                    new RecipeIngredient("iron_ingot", 3)
                )
            )
        );
    }

    private static void applyStairs(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
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
                "fallback_wooden_stairs",
                List.of(new RecipeIngredient(family.planksKey(), 6))
            )
        );
    }

    private static void applyBoat(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!family.supportsBoatRecipes()) {
            return;
        }
        if (!hasMaterial(family.planksKey()) || !hasMaterial(family.boatKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.boatKey())) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.boatKey(),
                1,
                "fallback_boat",
                List.of(new RecipeIngredient(family.planksKey(), 5))
            )
        );
    }

    private static void applyChestBoat(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!family.supportsBoatRecipes()) {
            return;
        }
        if (!hasMaterial(family.planksKey()) || !hasMaterial(family.chestBoatKey())) {
            return;
        }

        // Always add this fallback even if Bukkit already exposes a chest-boat recipe.
        // The built-in path can stay blocked when the generic chest path is unresolved.
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.chestBoatKey(),
                1,
                "fallback_chest_boat_from_planks",
                List.of(new RecipeIngredient(family.planksKey(), 13))
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

    private record WoodFamily(
        String familyKey,
        String inputLogKey,
        String strippedInputLogKey,
        String planksKey,
        String woodLikeOutputKey,
        String signKey,
        String hangingSignKey,
        String stairsKey,
        String boatKey,
        String chestBoatKey,
        boolean supportsBoatRecipes
    ) {

        private static WoodFamily normal(final String familyKey) {
            return new WoodFamily(
                familyKey,
                familyKey + "_log",
                "stripped_" + familyKey + "_log",
                familyKey + "_planks",
                familyKey + "_wood",
                familyKey + "_sign",
                familyKey + "_hanging_sign",
                familyKey + "_stairs",
                familyKey + "_boat",
                familyKey + "_chest_boat",
                true
            );
        }

        private static WoodFamily nether(final String familyKey) {
            return new WoodFamily(
                familyKey,
                familyKey + "_stem",
                "stripped_" + familyKey + "_stem",
                familyKey + "_planks",
                familyKey + "_hyphae",
                familyKey + "_sign",
                familyKey + "_hanging_sign",
                familyKey + "_stairs",
                null,
                null,
                false
            );
        }
    }
}

```
