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

        CommonCraftingRecipeNormalizers.apply(recipesByOutput);
        RecipeGraphFallbacks.apply(recipesByOutput);

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
