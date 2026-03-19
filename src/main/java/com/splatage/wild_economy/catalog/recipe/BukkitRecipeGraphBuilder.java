package com.splatage.wild_economy.catalog.recipe;

import com.splatage.wild_economy.catalog.scan.BukkitMaterialScanner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
                keys.add(BukkitMaterialScanner.normalizeKey(material));
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

        long combinations = 1L;
        for (final List<String> options : slotOptions) {
            combinations *= Math.max(1, options.size());
            if (combinations > MAX_COMBINATIONS_PER_RECIPE) {
                return Collections.emptyList();
            }
        }

        final List<RecipeDefinition> definitions = new ArrayList<>();
        this.expandRecipeSlots(outputKey, outputAmount, recipeType, slotOptions, 0, new ArrayList<>(), definitions);
        return List.copyOf(definitions);
    }

    private void expandRecipeSlots(
        final String outputKey,
        final int outputAmount,
        final String recipeType,
        final List<List<String>> slotOptions,
        final int slotIndex,
        final List<String> currentSelection,
        final List<RecipeDefinition> output
    ) {
        if (slotIndex >= slotOptions.size()) {
            output.add(new RecipeDefinition(
                outputKey,
                outputAmount,
                recipeType,
                this.collapseSelection(currentSelection)
            ));
            return;
        }

        for (final String option : slotOptions.get(slotIndex)) {
            currentSelection.add(option);
            this.expandRecipeSlots(outputKey, outputAmount, recipeType, slotOptions, slotIndex + 1, currentSelection, output);
            currentSelection.remove(currentSelection.size() - 1);
        }
    }

    private List<RecipeIngredient> collapseSelection(final List<String> selection) {
        final Map<String, Integer> counts = new HashMap<>();
        for (final String key : selection) {
            counts.merge(key, 1, Integer::sum);
        }

        final List<RecipeIngredient> ingredients = new ArrayList<>(counts.size());
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            ingredients.add(new RecipeIngredient(entry.getKey(), entry.getValue()));
        }
        ingredients.sort(java.util.Comparator.comparing(RecipeIngredient::itemKey));
        return List.copyOf(ingredients);
    }
}
