package com.splatage.wild_economy.catalog.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.Material;

final class UtilityRecipeFallbacks {

    private UtilityRecipeFallbacks() {
    }

    static void apply(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        applyChest(recipesByOutput);
        applyBarrel(recipesByOutput);
        applyFurnace(recipesByOutput);
        applyBow(recipesByOutput);
        applyDropper(recipesByOutput);
        applyDispenser(recipesByOutput);
        applyHopper(recipesByOutput);
        applyBlastFurnace(recipesByOutput);
        applyCrafter(recipesByOutput);
        applyComparator(recipesByOutput);

        applyBoneBlockCompression(recipesByOutput);
        applyBowlRecipes(recipesByOutput);
        applyBeetrootSoup(recipesByOutput);
        applyCampfireRecipes(recipesByOutput);
        applySmokerRecipes(recipesByOutput);
    }

    private static void applyChest(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("chest") || hasRecipes(recipesByOutput, "chest")) {
            return;
        }
        forEachWoodLikeFamily(
            family -> family.planksKey(),
            plankKey -> addRecipe(
                recipesByOutput,
                new RecipeDefinition(
                    "chest",
                    1,
                    "fallback_chest_from_planks",
                    List.of(new RecipeIngredient(plankKey, 8))
                )
            )
        );
    }

    private static void applyBarrel(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("barrel") || hasRecipes(recipesByOutput, "barrel")) {
            return;
        }
        for (final WoodLikeRecipeFamily family : WoodLikeRecipeFamilies.all()) {
            if (!hasMaterial(family.planksKey()) || !hasMaterial(family.slabKey())) {
                continue;
            }
            addRecipe(
                recipesByOutput,
                new RecipeDefinition(
                    "barrel",
                    1,
                    "fallback_barrel_from_planks_and_slab",
                    List.of(
                        new RecipeIngredient(family.planksKey(), 6),
                        new RecipeIngredient(family.slabKey(), 2)
                    )
                )
            );
        }
    }

    private static void applyFurnace(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("furnace") || hasRecipes(recipesByOutput, "furnace")) {
            return;
        }
        if (!hasMaterial("cobblestone")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "furnace",
                1,
                "fallback_furnace_from_cobblestone",
                List.of(new RecipeIngredient("cobblestone", 8))
            )
        );
    }

    private static void applyBow(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("bow") || hasRecipes(recipesByOutput, "bow")) {
            return;
        }
        if (!hasMaterial("stick") || !hasMaterial("string")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "bow",
                1,
                "fallback_bow_from_stick_and_string",
                List.of(
                    new RecipeIngredient("stick", 3),
                    new RecipeIngredient("string", 3)
                )
            )
        );
    }

    private static void applyDropper(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("dropper") || hasRecipes(recipesByOutput, "dropper")) {
            return;
        }
        if (!hasMaterial("cobblestone") || !hasMaterial("redstone")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "dropper",
                1,
                "fallback_dropper_from_cobblestone_and_redstone",
                List.of(
                    new RecipeIngredient("cobblestone", 7),
                    new RecipeIngredient("redstone", 1)
                )
            )
        );
    }

    private static void applyDispenser(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("dispenser") || hasRecipes(recipesByOutput, "dispenser")) {
            return;
        }
        if (!hasMaterial("cobblestone") || !hasMaterial("redstone") || !hasMaterial("bow")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "dispenser",
                1,
                "fallback_dispenser_from_cobblestone_bow_and_redstone",
                List.of(
                    new RecipeIngredient("bow", 1),
                    new RecipeIngredient("cobblestone", 7),
                    new RecipeIngredient("redstone", 1)
                )
            )
        );
    }

    private static void applyHopper(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("hopper") || hasRecipes(recipesByOutput, "hopper")) {
            return;
        }
        if (!hasMaterial("iron_ingot") || !hasMaterial("chest")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "hopper",
                1,
                "fallback_hopper_from_iron_and_chest",
                List.of(
                    new RecipeIngredient("chest", 1),
                    new RecipeIngredient("iron_ingot", 5)
                )
            )
        );
    }

    private static void applyBlastFurnace(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("blast_furnace") || hasRecipes(recipesByOutput, "blast_furnace")) {
            return;
        }
        if (!hasMaterial("furnace") || !hasMaterial("iron_ingot") || !hasMaterial("smooth_stone")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "blast_furnace",
                1,
                "fallback_blast_furnace_from_furnace_iron_and_smooth_stone",
                List.of(
                    new RecipeIngredient("furnace", 1),
                    new RecipeIngredient("iron_ingot", 5),
                    new RecipeIngredient("smooth_stone", 3)
                )
            )
        );
    }

    private static void applyCrafter(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("crafter") || hasRecipes(recipesByOutput, "crafter")) {
            return;
        }
        if (!hasMaterial("iron_ingot")
            || !hasMaterial("crafting_table")
            || !hasMaterial("redstone")
            || !hasMaterial("dropper")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "crafter",
                1,
                "fallback_crafter_from_iron_table_redstone_and_dropper",
                List.of(
                    new RecipeIngredient("crafting_table", 1),
                    new RecipeIngredient("dropper", 1),
                    new RecipeIngredient("iron_ingot", 5),
                    new RecipeIngredient("redstone", 2)
                )
            )
        );
    }

    private static void applyComparator(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("comparator") || hasRecipes(recipesByOutput, "comparator")) {
            return;
        }
        if (!hasMaterial("stone") || !hasMaterial("redstone_torch") || !hasMaterial("quartz")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "comparator",
                1,
                "fallback_comparator_from_stone_redstone_torch_and_quartz",
                List.of(
                    new RecipeIngredient("quartz", 1),
                    new RecipeIngredient("redstone_torch", 3),
                    new RecipeIngredient("stone", 3)
                )
            )
        );
    }

    private static void applyBoneBlockCompression(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("bone_block") || !hasMaterial("bone_meal")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "bone_block",
                1,
                "fallback_bone_block_from_bone_meal",
                List.of(new RecipeIngredient("bone_meal", 9))
            )
        );
    }

    private static void applyBowlRecipes(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("bowl")) {
            return;
        }
        forEachWoodLikeFamily(
            family -> family.planksKey(),
            plankKey -> addRecipe(
                recipesByOutput,
                new RecipeDefinition(
                    "bowl",
                    4,
                    "fallback_bowl_from_" + plankKey,
                    List.of(new RecipeIngredient(plankKey, 3))
                )
            )
        );
    }

    private static void applyBeetrootSoup(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("beetroot_soup") || !hasMaterial("beetroot") || !hasMaterial("bowl")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "beetroot_soup",
                1,
                "fallback_beetroot_soup",
                List.of(
                    new RecipeIngredient("beetroot", 6),
                    new RecipeIngredient("bowl", 1)
                )
            )
        );
    }

    private static void applyCampfireRecipes(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("campfire") || !hasMaterial("stick")) {
            return;
        }
        forEachWoodLikeFamily(
            family -> family.inputUnitKey(),
            inputUnitKey -> {
                if (hasMaterial("coal")) {
                    addRecipe(
                        recipesByOutput,
                        new RecipeDefinition(
                            "campfire",
                            1,
                            "fallback_campfire_from_" + inputUnitKey + "_and_coal",
                            List.of(
                                new RecipeIngredient("stick", 3),
                                new RecipeIngredient("coal", 1),
                                new RecipeIngredient(inputUnitKey, 3)
                            )
                        )
                    );
                }
                if (hasMaterial("charcoal")) {
                    addRecipe(
                        recipesByOutput,
                        new RecipeDefinition(
                            "campfire",
                            1,
                            "fallback_campfire_from_" + inputUnitKey + "_and_charcoal",
                            List.of(
                                new RecipeIngredient("stick", 3),
                                new RecipeIngredient("charcoal", 1),
                                new RecipeIngredient(inputUnitKey, 3)
                            )
                        )
                    );
                }
            }
        );
    }

    private static void applySmokerRecipes(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("smoker") || !hasMaterial("furnace")) {
            return;
        }
        forEachWoodLikeFamily(
            family -> family.inputUnitKey(),
            inputUnitKey -> addRecipe(
                recipesByOutput,
                new RecipeDefinition(
                    "smoker",
                    1,
                    "fallback_smoker_from_furnace_and_" + inputUnitKey,
                    List.of(
                        new RecipeIngredient("furnace", 1),
                        new RecipeIngredient(inputUnitKey, 4)
                    )
                )
            )
        );
    }

    private static void forEachWoodLikeFamily(
        final Function<WoodLikeRecipeFamily, String> inputSelector,
        final java.util.function.Consumer<String> consumer
    ) {
        for (final WoodLikeRecipeFamily family : WoodLikeRecipeFamilies.all()) {
            final String itemKey = inputSelector.apply(family);
            if (itemKey == null || !hasMaterial(itemKey)) {
                continue;
            }
            consumer.accept(itemKey);
        }
    }

    private static boolean hasRecipes(final Map<String, List<RecipeDefinition>> recipesByOutput, final String outputKey) {
        final List<RecipeDefinition> definitions = recipesByOutput.get(outputKey);
        return definitions != null && !definitions.isEmpty();
    }

    private static void addRecipe(final Map<String, List<RecipeDefinition>> recipesByOutput, final RecipeDefinition recipeDefinition) {
        recipesByOutput.computeIfAbsent(recipeDefinition.outputKey(), ignored -> new ArrayList<>()).add(recipeDefinition);
    }

    private static boolean hasMaterial(final String itemKey) {
        return Material.matchMaterial(itemKey.toUpperCase(Locale.ROOT)) != null;
    }
}
