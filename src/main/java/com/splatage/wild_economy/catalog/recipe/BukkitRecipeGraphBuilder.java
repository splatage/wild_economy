package com.splatage.wild_economy.catalog.recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

public final class BukkitRecipeGraphBuilder {

    private static final int MAX_COMBINATIONS_PER_RECIPE = 144;
    private static final int MAX_DROPPED_RECIPE_LOG_LINES = 25;

    private static final Set<String> TARGET_DIAGNOSTIC_OUTPUTS = Set.of(
        "clock",
        "compass",
        "carrot_on_a_stick",
        "copper_axe",
        "copper_pickaxe",
        "crossbow"
    );

    private final List<DroppedRecipeExpansion> droppedRecipeExpansions = new ArrayList<>();
    private final Map<String, List<String>> extractionDiagnostics = new LinkedHashMap<>();

    public RecipeGraph build() {
        this.droppedRecipeExpansions.clear();
        this.extractionDiagnostics.clear();

        final Map<String, List<RecipeDefinition>> recipesByOutput = new LinkedHashMap<>();
        final Iterator<Recipe> iterator = Bukkit.getServer().recipeIterator();

        while (iterator.hasNext()) {
            final Recipe recipe = iterator.next();
            final ItemStack result = recipe.getResult();
            if (result == null || result.getType() == null || result.getType() == Material.AIR) {
                continue;
            }

            final String outputKey = this.normalizeMaterialKey(result.getType());
            this.recordDiagnostic(outputKey, "saw recipe class=" + recipe.getClass().getName());

            final int outputAmount = Math.max(1, result.getAmount());
            final List<RecipeDefinition> extracted = this.extractRecipeDefinitions(recipe, outputKey, outputAmount);
            if (extracted.isEmpty()) {
                this.recordDiagnostic(outputKey, "extraction returned empty");
                continue;
            }

            this.recordDiagnostic(outputKey, "extracted definitions=" + extracted.size());
            recipesByOutput.computeIfAbsent(outputKey, ignored -> new ArrayList<>()).addAll(extracted);
        }

        RecipeGraphFallbacks.apply(recipesByOutput);
        this.logDroppedRecipeExpansions();
        this.logTargetDiagnostics(recipesByOutput);

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
            this.recordDiagnostic(outputKey, "using shaped extraction");
            return this.extractShapedRecipes(shapedRecipe, outputKey, outputAmount, this.describeRecipeType(recipe, "shaped"));
        }
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            this.recordDiagnostic(outputKey, "using shapeless extraction");
            return this.extractShapelessRecipes(shapelessRecipe, outputKey, outputAmount, this.describeRecipeType(recipe, "shapeless"));
        }
        if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
            this.recordDiagnostic(outputKey, "using stonecutting extraction");
            return this.extractSingleInputRecipe(
                outputKey,
                outputAmount,
                this.describeRecipeType(recipe, "stonecutting"),
                stonecuttingRecipe.getInputChoice()
            );
        }
        if (recipe instanceof CookingRecipe<?> cookingRecipe) {
            this.recordDiagnostic(outputKey, "using cooking extraction");
            return this.extractSingleInputRecipe(
                outputKey,
                outputAmount,
                this.describeRecipeType(recipe, recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT)),
                cookingRecipe.getInputChoice()
            );
        }

        final List<RecipeDefinition> smithingLike = this.extractSmithingLikeRecipeByReflection(recipe, outputKey, outputAmount);
        if (!smithingLike.isEmpty()) {
            this.recordDiagnostic(outputKey, "using reflected smithing-like extraction");
            return smithingLike;
        }

        final List<RecipeDefinition> reflectedSingleInput = this.extractSingleInputRecipeByReflection(recipe, outputKey, outputAmount);
        if (!reflectedSingleInput.isEmpty()) {
            this.recordDiagnostic(outputKey, "using reflected single-input extraction");
            return reflectedSingleInput;
        }

        this.recordDiagnostic(outputKey, "no extraction path matched");
        return Collections.emptyList();
    }

    private List<RecipeDefinition> extractShapedRecipes(
        final ShapedRecipe recipe,
        final String outputKey,
        final int outputAmount,
        final String recipeType
    ) {
        final Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();
        final Map<Character, ItemStack> ingredientMap = recipe.getIngredientMap();
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

                final Object choiceValue = choiceMap.get(key);
                final Object ingredientValue = ingredientMap.get(key);

                List<String> options = this.resolveChoiceOptions(choiceValue);
                if (options.isEmpty()) {
                    options = this.resolveChoiceOptions(ingredientValue);
                }

                if (options.isEmpty()) {
                    if (choiceValue == null && ingredientValue == null) {
                        this.recordDiagnostic(outputKey, "shaped extraction: treating key '" + key + "' as empty slot");
                        continue;
                    }

                    this.recordDiagnostic(outputKey, "shaped extraction failed: empty options for key '" + key + "'");
                    this.recordDiagnostic(outputKey, "shape=" + Arrays.toString(recipe.getShape()));
                    this.recordDiagnostic(outputKey, "choiceMap keys=" + choiceMap.keySet());
                    this.recordDiagnostic(outputKey, "ingredientMap keys=" + ingredientMap.keySet());
                    this.recordDiagnostic(outputKey, "choiceMap[" + key + "]=" + this.describeDebugObject(choiceValue));
                    this.recordDiagnostic(outputKey, "ingredientMap[" + key + "]=" + this.describeDebugObject(ingredientValue));
                    return Collections.emptyList();
                }

                slotOptions.add(options);
            }
        }

        return this.expandRecipes(outputKey, outputAmount, recipeType, slotOptions);
    }

    private List<RecipeDefinition> extractShapelessRecipes(
        final ShapelessRecipe recipe,
        final String outputKey,
        final int outputAmount,
        final String recipeType
    ) {
        final List<RecipeChoice> choiceList = recipe.getChoiceList();
        final List<ItemStack> ingredientList = recipe.getIngredientList();
        final int slotCount = Math.max(choiceList.size(), ingredientList.size());
        final List<List<String>> slotOptions = new ArrayList<>(slotCount);

        for (int i = 0; i < slotCount; i++) {
            List<String> options = Collections.emptyList();
            if (i < choiceList.size()) {
                options = this.resolveChoiceOptions(choiceList.get(i));
            }
            if (options.isEmpty() && i < ingredientList.size()) {
                options = this.resolveChoiceOptions(ingredientList.get(i));
            }
            if (options.isEmpty()) {
                this.recordDiagnostic(outputKey, "shapeless extraction failed: empty options at slot " + i);
                return Collections.emptyList();
            }
            slotOptions.add(options);
        }

        return this.expandRecipes(outputKey, outputAmount, recipeType, slotOptions);
    }

    private List<RecipeDefinition> extractSingleInputRecipe(
        final String outputKey,
        final int outputAmount,
        final String recipeType,
        final Object inputChoice
    ) {
        final List<String> options = this.resolveChoiceOptions(inputChoice);
        if (options.isEmpty()) {
            this.recordDiagnostic(outputKey, "single-input extraction failed: empty options");
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

    private List<RecipeDefinition> extractSingleInputRecipeByReflection(
        final Recipe recipe,
        final String outputKey,
        final int outputAmount
    ) {
        final Object inputChoice = this.invokeNoArg(recipe, "getInputChoice");
        if (inputChoice != null) {
            return this.extractSingleInputRecipe(
                outputKey,
                outputAmount,
                this.describeRecipeType(recipe, recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT)),
                inputChoice
            );
        }

        final Object input = this.invokeNoArg(recipe, "getInput");
        final List<String> options = this.resolveChoiceOptions(input);
        if (options.isEmpty()) {
            this.recordDiagnostic(outputKey, "reflected single-input extraction failed: empty options");
            return Collections.emptyList();
        }

        final List<RecipeDefinition> definitions = new ArrayList<>(options.size());
        for (final String option : options) {
            definitions.add(new RecipeDefinition(
                outputKey,
                outputAmount,
                this.describeRecipeType(recipe, recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT)),
                List.of(new RecipeIngredient(option, 1))
            ));
        }
        return List.copyOf(definitions);
    }

    private List<RecipeDefinition> extractSmithingLikeRecipeByReflection(
        final Recipe recipe,
        final String outputKey,
        final int outputAmount
    ) {
        final List<List<String>> slotOptions = new ArrayList<>();

        final Object baseChoice = this.invokeNoArg(recipe, "getBase");
        if (baseChoice != null) {
            final List<String> baseOptions = this.resolveChoiceOptions(baseChoice);
            if (baseOptions.isEmpty()) {
                this.recordDiagnostic(outputKey, "smithing-like extraction failed: empty base options");
                return Collections.emptyList();
            }
            slotOptions.add(baseOptions);
        }

        final Object additionChoice = this.invokeNoArg(recipe, "getAddition");
        if (additionChoice != null) {
            final List<String> additionOptions = this.resolveChoiceOptions(additionChoice);
            if (additionOptions.isEmpty()) {
                this.recordDiagnostic(outputKey, "smithing-like extraction failed: empty addition options");
                return Collections.emptyList();
            }
            slotOptions.add(additionOptions);
        }

        final Object templateChoice = this.invokeNoArg(recipe, "getTemplate");
        if (templateChoice != null) {
            final List<String> templateOptions = this.resolveChoiceOptions(templateChoice);
            if (templateOptions.isEmpty()) {
                this.recordDiagnostic(outputKey, "smithing-like extraction failed: empty template options");
                return Collections.emptyList();
            }
            slotOptions.add(templateOptions);
        }

        if (slotOptions.isEmpty()) {
            return Collections.emptyList();
        }

        return this.expandRecipes(
            outputKey,
            outputAmount,
            this.describeRecipeType(recipe, recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT)),
            slotOptions
        );
    }

    private Object invokeNoArg(final Object target, final String methodName) {
        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String describeRecipeType(final Recipe recipe, final String baseType) {
        if (recipe instanceof Keyed keyed) {
            return keyed.getKey() + "/" + baseType;
        }
        return baseType;
    }

    private List<String> resolveChoiceOptions(final Object choiceOrInput) {
        if (choiceOrInput == null) {
            return Collections.emptyList();
        }

        if (choiceOrInput instanceof RecipeChoice.MaterialChoice materialChoice) {
            final List<String> keys = new ArrayList<>();
            for (final Material material : materialChoice.getChoices()) {
                if (material == null || material == Material.AIR || !material.isItem()) {
                    continue;
                }
                final String key = this.normalizeMaterialKey(material);
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
            return List.copyOf(keys);
        }

        if (choiceOrInput instanceof RecipeChoice.ExactChoice exactChoice) {
            final List<String> keys = new ArrayList<>();
            for (final ItemStack itemStack : exactChoice.getChoices()) {
                if (itemStack == null || itemStack.getType() == null || itemStack.getType() == Material.AIR) {
                    continue;
                }
                if (!itemStack.getType().isItem()) {
                    continue;
                }
                final String key = this.normalizeMaterialKey(itemStack.getType());
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
            return List.copyOf(keys);
        }

        if (choiceOrInput instanceof Material material) {
            if (material == Material.AIR || !material.isItem()) {
                return Collections.emptyList();
            }
            return List.of(this.normalizeMaterialKey(material));
        }

        if (choiceOrInput instanceof ItemStack itemStack) {
            if (itemStack.getType() == null || itemStack.getType() == Material.AIR || !itemStack.getType().isItem()) {
                return Collections.emptyList();
            }
            return List.of(this.normalizeMaterialKey(itemStack.getType()));
        }

        final Object rawChoices = this.invokeNoArg(choiceOrInput, "getChoices");
        if (rawChoices instanceof Iterable<?> iterable) {
            final List<String> keys = new ArrayList<>();
            for (final Object entry : iterable) {
                if (entry instanceof Material material) {
                    if (material == Material.AIR || !material.isItem()) {
                        continue;
                    }
                    final String key = this.normalizeMaterialKey(material);
                    if (!keys.contains(key)) {
                        keys.add(key);
                    }
                    continue;
                }
                if (entry instanceof ItemStack itemStack) {
                    if (itemStack.getType() == null || itemStack.getType() == Material.AIR || !itemStack.getType().isItem()) {
                        continue;
                    }
                    final String key = this.normalizeMaterialKey(itemStack.getType());
                    if (!keys.contains(key)) {
                        keys.add(key);
                    }
                }
            }
            return List.copyOf(keys);
        }

        return Collections.emptyList();
    }

    private String normalizeMaterialKey(final Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    private String describeDebugObject(final Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof RecipeChoice.MaterialChoice materialChoice) {
            return value.getClass().getName() + "{choices=" + materialChoice.getChoices() + "}";
        }

        if (value instanceof RecipeChoice.ExactChoice exactChoice) {
            final List<String> renderedChoices = new ArrayList<>();
            for (final ItemStack itemStack : exactChoice.getChoices()) {
                if (itemStack == null) {
                    renderedChoices.add("null");
                } else {
                    renderedChoices.add(itemStack.getType() + "x" + itemStack.getAmount());
                }
            }
            return value.getClass().getName() + "{choices=" + renderedChoices + "}";
        }

        if (value instanceof ItemStack itemStack) {
            return value.getClass().getName() + "{type=" + itemStack.getType() + ", amount=" + itemStack.getAmount() + "}";
        }

        return value.getClass().getName() + "{value=" + value + "}";
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
                this.droppedRecipeExpansions.add(new DroppedRecipeExpansion(
                    outputKey,
                    recipeType,
                    combinations,
                    groupedOptions.stream().map(grouped -> grouped.options().size()).toList()
                ));
                this.recordDiagnostic(
                    outputKey,
                    "dropped by combination cap; combinations=" + combinations
                        + ", option-sizes=" + groupedOptions.stream().map(grouped -> grouped.options().size()).toList()
                );
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

    private void logDroppedRecipeExpansions() {
        if (this.droppedRecipeExpansions.isEmpty()) {
            return;
        }

        Bukkit.getLogger().warning("[wild_economy] Dropped " + this.droppedRecipeExpansions.size()
            + " recipe expansions because they exceeded the " + MAX_COMBINATIONS_PER_RECIPE + "-combination cap.");

        int logged = 0;
        for (final DroppedRecipeExpansion dropped : this.droppedRecipeExpansions) {
            if (logged >= MAX_DROPPED_RECIPE_LOG_LINES) {
                break;
            }
            Bukkit.getLogger().warning("[wild_economy] Dropped recipe expansion for " + dropped.outputKey()
                + " (" + dropped.recipeType() + "), combinations=" + dropped.combinations()
                + ", option-sizes=" + dropped.optionSizes());
            logged++;
        }

        if (this.droppedRecipeExpansions.size() > MAX_DROPPED_RECIPE_LOG_LINES) {
            Bukkit.getLogger().warning("[wild_economy] Additional dropped recipe expansions omitted from log: "
                + (this.droppedRecipeExpansions.size() - MAX_DROPPED_RECIPE_LOG_LINES));
        }
    }

    private void recordDiagnostic(final String outputKey, final String message) {
        if (!TARGET_DIAGNOSTIC_OUTPUTS.contains(outputKey)) {
            return;
        }
        this.extractionDiagnostics.computeIfAbsent(outputKey, ignored -> new ArrayList<>()).add(message);
    }

    private void logTargetDiagnostics(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        for (final String outputKey : TARGET_DIAGNOSTIC_OUTPUTS) {
            final List<String> messages = this.extractionDiagnostics.get(outputKey);
            if (messages == null || messages.isEmpty()) {
                Bukkit.getLogger().info("[wild_economy] Recipe extraction diagnostic for '" + outputKey
                    + "': no recipe activity recorded.");
            } else {
                for (final String message : messages) {
                    Bukkit.getLogger().info("[wild_economy] Recipe extraction diagnostic for '" + outputKey
                        + "': " + message);
                }
            }

            final List<RecipeDefinition> definitions = recipesByOutput.get(outputKey);
            final int count = definitions == null ? 0 : definitions.size();
            Bukkit.getLogger().info("[wild_economy] Recipe extraction diagnostic for '" + outputKey
                + "': final definitions in graph=" + count);
        }
    }

    private record SlotOptionGroup(List<String> options, int repeats) {
    }

    private record DroppedRecipeExpansion(String outputKey, String recipeType, long combinations, List<Integer> optionSizes) {
    }
}
