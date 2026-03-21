# wild_economy Phase 1 recipe coverage patch (`bca35e2a6b15ebdd63154830cec58bc67703ff2f`)

This bundle contains complete replacement files for the next Phase 1 catalog-quality pass.

Scope of this pass:
- keep the Phase 1 admin/rule pipeline unchanged
- improve recipe graph coverage for tag-heavy recipes
- add stripped-log fallback derivation for hanging-sign style wood recipes

---

## File: `src/main/java/com/splatage/wild_economy/catalog/recipe/BukkitRecipeGraphBuilder.java`

```java
package com.splatage.wild_economy.catalog.recipe;

import com.splatage.wild_economy.catalog.scan.BukkitMaterialScanner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

public final class BukkitRecipeGraphBuilder {

    private static final int MAX_COMBINATIONS_PER_RECIPE = 128;

    public RecipeGraph build() {
        final Map<String, List<RecipeDefinition>> recipesByOutput = new LinkedHashMap<>();
        final Iterator<Recipe> iterator = Bukkit.getServer().recipeIterator();

        while (iterator.hasNext()) {
            final Recipe recipe = iterator.next();
            final ItemStack result = recipe.getResult();
            if (result == null || result.getType() == null || result.getType() == Material.AIR) {
                continue;
            }

            final String outputKey = BukkitMaterialScanner.normalizeKey(result.getType());
            final int outputAmount = Math.max(1, result.getAmount());
            final List<RecipeDefinition> extracted = this.extractRecipeDefinitions(recipe, outputKey, outputAmount);
            if (extracted.isEmpty()) {
                continue;
            }

            recipesByOutput.computeIfAbsent(outputKey, ignored -> new ArrayList<>()).addAll(extracted);
        }

        WoodFamilyRecipeFallbacks.apply(recipesByOutput);

        final Map<String, List<RecipeDefinition>> frozen = new LinkedHashMap<>();
        for (final Map.Entry<String, List<RecipeDefinition>> entry : recipesByOutput.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return new RecipeGraph(frozen);
    }

    private List<RecipeDefinition> extractRecipeDefinitions(
        final Recipe recipe,
        final String outputKey,
        final int outputAmount
    ) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return this.extractShapedRecipes(shapedRecipe, outputKey, outputAmount);
        }
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            return this.extractShapelessRecipes(shapelessRecipe, outputKey, outputAmount);
        }
        if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
            return this.extractSingleInputRecipe(
                outputKey,
                outputAmount,
                "stonecutting",
                stonecuttingRecipe.getInputChoice()
            );
        }
        if (recipe instanceof CookingRecipe<?> cookingRecipe) {
            return this.extractSingleInputRecipe(
                outputKey,
                outputAmount,
                recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT),
                cookingRecipe.getInputChoice()
            );
        }

        return Collections.emptyList();
    }

    private List<RecipeDefinition> extractShapedRecipes(
        final ShapedRecipe recipe,
        final String outputKey,
        final int outputAmount
    ) {
        final Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();
        final List<List<String>> slotOptions = new ArrayList<>();

        for (final String row : recipe.getShape()) {
            if (row == null) {
                continue;
            }
            for (int i = 0; i < row.length(); i++) {
                final char key = row.charAt(i);
                if (key == ' ') {
                    continue;
                }
                final RecipeChoice choice = choiceMap.get(key);
                final List<String> options = this.resolveChoiceOptions(choice);
                if (options.isEmpty()) {
                    return Collections.emptyList();
                }
                slotOptions.add(options);
            }
        }

        return this.expandRecipes(outputKey, outputAmount, "shaped", slotOptions);
    }

    private List<RecipeDefinition> extractShapelessRecipes(
        final ShapelessRecipe recipe,
        final String outputKey,
        final int outputAmount
    ) {
        final List<List<String>> slotOptions = new ArrayList<>();
        for (final RecipeChoice choice : recipe.getChoiceList()) {
            final List<String> options = this.resolveChoiceOptions(choice);
            if (options.isEmpty()) {
                return Collections.emptyList();
            }
            slotOptions.add(options);
        }

        return this.expandRecipes(outputKey, outputAmount, "shapeless", slotOptions);
    }

    private List<RecipeDefinition> extractSingleInputRecipe(
        final String outputKey,
        final int outputAmount,
        final String recipeType,
        final RecipeChoice inputChoice
    ) {
        final List<String> options = this.resolveChoiceOptions(inputChoice);
        if (options.isEmpty()) {
            return Collections.emptyList();
        }

        final List<RecipeDefinition> definitions = new ArrayList<>(options.size());
        for (final String option : options) {
            definitions.add(new RecipeDefinition(
                outputKey,
                outputAmount,
                recipeType,
                List.of(new RecipeIngredient(option, 1))
            ));
        }
        return List.copyOf(definitions);
    }

    private List<String> resolveChoiceOptions(final RecipeChoice choice) {
        if (choice == null) {
            return Collections.emptyList();
        }

        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            final List<String> keys = new ArrayList<>();
            for (final Material material : materialChoice.getChoices()) {
                if (material == null || material == Material.AIR || !material.isItem()) {
                    continue;
                }
                final String key = BukkitMaterialScanner.normalizeKey(material);
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
            return List.copyOf(keys);
        }

        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            final List<String> keys = new ArrayList<>();
            for (final ItemStack itemStack : exactChoice.getChoices()) {
                if (itemStack == null || itemStack.getType() == null || itemStack.getType() == Material.AIR) {
                    continue;
                }
                if (!itemStack.getType().isItem()) {
                    continue;
                }
                final String key = BukkitMaterialScanner.normalizeKey(itemStack.getType());
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
            return List.copyOf(keys);
        }

        return Collections.emptyList();
    }

    private List<RecipeDefinition> expandRecipes(
        final String outputKey,
        final int outputAmount,
        final String recipeType,
        final List<List<String>> slotOptions
    ) {
        if (slotOptions.isEmpty()) {
            return Collections.emptyList();
        }

        final List<SlotOptionGroup> groupedOptions = this.groupSlotOptions(slotOptions);
        long combinations = 1L;
        for (final SlotOptionGroup group : groupedOptions) {
            combinations *= Math.max(1, group.options().size());
            if (combinations > MAX_COMBINATIONS_PER_RECIPE) {
                return Collections.emptyList();
            }
        }

        final List<RecipeDefinition> definitions = new ArrayList<>();
        this.expandRecipeGroups(
            outputKey,
            outputAmount,
            recipeType,
            groupedOptions,
            0,
            new LinkedHashMap<>(),
            definitions
        );
        return List.copyOf(definitions);
    }

    private List<SlotOptionGroup> groupSlotOptions(final List<List<String>> slotOptions) {
        final LinkedHashMap<List<String>, Integer> grouped = new LinkedHashMap<>();
        for (final List<String> options : slotOptions) {
            final List<String> key = List.copyOf(options);
            grouped.merge(key, 1, Integer::sum);
        }

        final List<SlotOptionGroup> output = new ArrayList<>(grouped.size());
        for (final Map.Entry<List<String>, Integer> entry : grouped.entrySet()) {
            output.add(new SlotOptionGroup(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(output);
    }

    private void expandRecipeGroups(
        final String outputKey,
        final int outputAmount,
        final String recipeType,
        final List<SlotOptionGroup> groupedOptions,
        final int groupIndex,
        final Map<String, Integer> currentIngredientCounts,
        final List<RecipeDefinition> output
    ) {
        if (groupIndex >= groupedOptions.size()) {
            output.add(new RecipeDefinition(
                outputKey,
                outputAmount,
                recipeType,
                this.collapseSelection(currentIngredientCounts)
            ));
            return;
        }

        final SlotOptionGroup group = groupedOptions.get(groupIndex);
        for (final String option : group.options()) {
            currentIngredientCounts.merge(option, group.repeats(), Integer::sum);
            this.expandRecipeGroups(
                outputKey,
                outputAmount,
                recipeType,
                groupedOptions,
                groupIndex + 1,
                currentIngredientCounts,
                output
            );

            final int remaining = currentIngredientCounts.get(option) - group.repeats();
            if (remaining <= 0) {
                currentIngredientCounts.remove(option);
            } else {
                currentIngredientCounts.put(option, remaining);
            }
        }
    }

    private List<RecipeIngredient> collapseSelection(final Map<String, Integer> ingredientCounts) {
        final List<RecipeIngredient> ingredients = new ArrayList<>(ingredientCounts.size());
        for (final Map.Entry<String, Integer> entry : ingredientCounts.entrySet()) {
            ingredients.add(new RecipeIngredient(entry.getKey(), entry.getValue()));
        }
        ingredients.sort(Comparator.comparing(RecipeIngredient::itemKey));
        return List.copyOf(ingredients);
    }

    private record SlotOptionGroup(List<String> options, int repeats) {
    }
}

```

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
        if (!hasMaterial(family.strippedInputLogKey()) || !hasMaterial(family.hangingSignKey()) || !hasMaterial("chain")) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.hangingSignKey())) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.hangingSignKey(),
                6,
                "fallback_hanging_sign",
                List.of(
                    new RecipeIngredient(family.strippedInputLogKey(), 6),
                    new RecipeIngredient("chain", 2)
                )
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
        if (!hasMaterial(family.boatKey()) || !hasMaterial(family.chestBoatKey()) || !hasMaterial("chest")) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.chestBoatKey())) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.chestBoatKey(),
                1,
                "fallback_chest_boat",
                List.of(
                    new RecipeIngredient(family.boatKey(), 1),
                    new RecipeIngredient("chest", 1)
                )
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
                null,
                null,
                false
            );
        }
    }
}

```

