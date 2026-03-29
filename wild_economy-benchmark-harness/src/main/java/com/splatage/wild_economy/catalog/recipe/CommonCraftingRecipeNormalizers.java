package com.splatage.wild_economy.catalog.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Material;

final class CommonCraftingRecipeNormalizers {

    private CommonCraftingRecipeNormalizers() {
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
        applyBucket(recipesByOutput);
        applyCauldron(recipesByOutput);
        applyAnvil(recipesByOutput);
        applyBrewingStand(recipesByOutput);
        applyConcreteConversions(recipesByOutput);
        applyCopperOxidationAndWaxingTransitions(recipesByOutput);
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
                    "normalized_chest_from_planks",
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
                    "normalized_barrel_from_planks_and_slab",
                    List.of(
                        new RecipeIngredient(family.planksKey(), 6),
                        new RecipeIngredient(family.slabKey(), 2)
                    )
                )
            );
        }
    }

    private static void applyFurnace(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("furnace") || hasRecipes(recipesByOutput, "furnace") || !hasMaterial("cobblestone")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "furnace",
                1,
                "normalized_furnace_from_cobblestone",
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
                "normalized_bow_from_stick_and_string",
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
                "normalized_dropper_from_cobblestone_and_redstone",
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
                "normalized_dispenser_from_cobblestone_bow_and_redstone",
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
                "normalized_hopper_from_iron_and_chest",
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
                "normalized_blast_furnace_from_furnace_iron_and_smooth_stone",
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
                "normalized_crafter_from_iron_table_redstone_and_dropper",
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
                "normalized_comparator_from_stone_redstone_torch_and_quartz",
                List.of(
                    new RecipeIngredient("quartz", 1),
                    new RecipeIngredient("redstone_torch", 3),
                    new RecipeIngredient("stone", 3)
                )
            )
        );
    }

    private static void applyBoneBlockCompression(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("bone_block") || !hasMaterial("bone_meal") || hasRecipes(recipesByOutput, "bone_block")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "bone_block",
                1,
                "normalized_bone_block_from_bone_meal",
                List.of(new RecipeIngredient("bone_meal", 9))
            )
        );
    }

    private static void applyBucket(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("bucket") || hasRecipes(recipesByOutput, "bucket") || !hasMaterial("iron_ingot")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "bucket",
                1,
                "normalized_bucket_from_iron_ingot",
                List.of(new RecipeIngredient("iron_ingot", 3))
            )
        );
    }

    private static void applyCauldron(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("cauldron") || hasRecipes(recipesByOutput, "cauldron") || !hasMaterial("iron_ingot")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "cauldron",
                1,
                "normalized_cauldron_from_iron_ingot",
                List.of(new RecipeIngredient("iron_ingot", 7))
            )
        );
    }

    private static void applyAnvil(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("anvil") || hasRecipes(recipesByOutput, "anvil")) {
            return;
        }
        if (!hasMaterial("iron_block") || !hasMaterial("iron_ingot")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "anvil",
                1,
                "normalized_anvil_from_iron_block_and_iron_ingot",
                List.of(
                    new RecipeIngredient("iron_block", 3),
                    new RecipeIngredient("iron_ingot", 4)
                )
            )
        );
    }

    private static void applyBrewingStand(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("brewing_stand") || hasRecipes(recipesByOutput, "brewing_stand")) {
            return;
        }
        if (!hasMaterial("blaze_rod") || !hasMaterial("cobblestone")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "brewing_stand",
                1,
                "normalized_brewing_stand_from_blaze_rod_and_cobblestone",
                List.of(
                    new RecipeIngredient("blaze_rod", 1),
                    new RecipeIngredient("cobblestone", 3)
                )
            )
        );
    }

    private static void applyConcreteConversions(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        for (final String color : List.of(
            "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"
        )) {
            final String concreteKey = color + "_concrete";
            final String powderKey = color + "_concrete_powder";
            if (!hasMaterial(concreteKey) || !hasMaterial(powderKey) || hasRecipes(recipesByOutput, concreteKey)) {
                continue;
            }
            addRecipe(
                recipesByOutput,
                new RecipeDefinition(
                    concreteKey,
                    1,
                    "normalized_" + concreteKey + "_from_powder",
                    List.of(new RecipeIngredient(powderKey, 1))
                )
            );
        }
    }

    private static void applyCopperOxidationAndWaxingTransitions(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        final List<String> copperFamilyKeys = List.of(
            "copper_block",
            "chiseled_copper",
            "cut_copper",
            "cut_copper_slab",
            "cut_copper_stairs",
            "copper_bars",
            "copper_bulb",
            "copper_chain",
            "copper_chest",
            "copper_door",
            "copper_golem_statue",
            "copper_grate",
            "copper_lantern",
            "copper_trapdoor",
            "lightning_rod"
        );

        for (final String baseKey : copperFamilyKeys) {
            addOxidationTransition(recipesByOutput, baseKey, oxidationTargetFor(baseKey, "exposed_"), "normalized_exposed_" + baseKey + "_from_" + baseKey);
            addOxidationTransition(recipesByOutput, oxidationTargetFor(baseKey, "exposed_"), oxidationTargetFor(baseKey, "weathered_"), "normalized_weathered_" + baseKey + "_from_exposed_" + baseKey);
            addOxidationTransition(recipesByOutput, oxidationTargetFor(baseKey, "weathered_"), oxidationTargetFor(baseKey, "oxidized_"), "normalized_oxidized_" + baseKey + "_from_weathered_" + baseKey);

            addWaxingTransition(recipesByOutput, baseKey, "waxed_" + baseKey, "normalized_waxed_" + baseKey + "_from_" + baseKey);
            addWaxingTransition(recipesByOutput, oxidationTargetFor(baseKey, "exposed_"), "waxed_" + oxidationTargetFor(baseKey, "exposed_"), "normalized_waxed_exposed_" + baseKey + "_from_exposed_" + baseKey);
            addWaxingTransition(recipesByOutput, oxidationTargetFor(baseKey, "weathered_"), "waxed_" + oxidationTargetFor(baseKey, "weathered_"), "normalized_waxed_weathered_" + baseKey + "_from_weathered_" + baseKey);
            addWaxingTransition(recipesByOutput, oxidationTargetFor(baseKey, "oxidized_"), "waxed_" + oxidationTargetFor(baseKey, "oxidized_"), "normalized_waxed_oxidized_" + baseKey + "_from_oxidized_" + baseKey);
        }
    }

    private static String oxidationTargetFor(final String baseKey, final String prefix) {
        if ("copper_block".equals(baseKey)) {
            return prefix.equals("exposed_") ? "exposed_copper"
                : prefix.equals("weathered_") ? "weathered_copper"
                : "oxidized_copper";
        }
        return prefix + baseKey;
    }

    private static void addOxidationTransition(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final String sourceKey,
        final String targetKey,
        final String recipeType
    ) {
        if (!hasMaterial(sourceKey) || !hasMaterial(targetKey) || hasRecipes(recipesByOutput, targetKey)) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                targetKey,
                1,
                recipeType,
                List.of(new RecipeIngredient(sourceKey, 1))
            )
        );
    }

    private static void addWaxingTransition(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final String sourceKey,
        final String targetKey,
        final String recipeType
    ) {
        if (!hasMaterial(sourceKey) || !hasMaterial(targetKey) || hasRecipes(recipesByOutput, targetKey) || !hasMaterial("honeycomb")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                targetKey,
                1,
                recipeType,
                List.of(
                    new RecipeIngredient(sourceKey, 1),
                    new RecipeIngredient("honeycomb", 1)
                )
            )
        );
    }

    private static void applyBowlRecipes(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("bowl") || hasRecipes(recipesByOutput, "bowl")) {
            return;
        }
        forEachWoodLikeFamily(
            family -> family.planksKey(),
            plankKey -> addRecipe(
                recipesByOutput,
                new RecipeDefinition(
                    "bowl",
                    4,
                    "normalized_bowl_from_" + plankKey,
                    List.of(new RecipeIngredient(plankKey, 3))
                )
            )
        );
    }

    private static void applyBeetrootSoup(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("beetroot_soup") || hasRecipes(recipesByOutput, "beetroot_soup")) {
            return;
        }
        if (!hasMaterial("beetroot") || !hasMaterial("bowl")) {
            return;
        }
        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                "beetroot_soup",
                1,
                "normalized_beetroot_soup",
                List.of(
                    new RecipeIngredient("beetroot", 6),
                    new RecipeIngredient("bowl", 1)
                )
            )
        );
    }

    private static void applyCampfireRecipes(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        if (!hasMaterial("campfire") || hasRecipes(recipesByOutput, "campfire") || !hasMaterial("stick")) {
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
                            "normalized_campfire_from_" + inputUnitKey + "_and_coal",
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
                            "normalized_campfire_from_" + inputUnitKey + "_and_charcoal",
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
        if (!hasMaterial("smoker") || hasRecipes(recipesByOutput, "smoker") || !hasMaterial("furnace")) {
            return;
        }
        forEachWoodLikeFamily(
            family -> family.inputUnitKey(),
            inputUnitKey -> addRecipe(
                recipesByOutput,
                new RecipeDefinition(
                    "smoker",
                    1,
                    "normalized_smoker_from_furnace_and_" + inputUnitKey,
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
        final Consumer<String> consumer
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
