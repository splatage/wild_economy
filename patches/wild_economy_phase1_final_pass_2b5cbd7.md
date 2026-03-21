# wild_economy Phase 1 final pass (`2b5cbd7b153db8d7d03498e3f71c188d74aca1bc`)

This bundle contains complete replacement files for the final narrow Phase 1 quality pass.

Scope of this pass:
- close the remaining wood-family derivation gaps for chest boats and hanging signs
- reduce one obvious wood-family `MISC` spillover case (`*_shelf`)

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
        if (!hasMaterial("iron_ingot") || !hasMaterial("iron_nugget")) {
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
                    new RecipeIngredient("iron_ingot", 2),
                    new RecipeIngredient("iron_nugget", 4)
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
        if (hasRecipes(recipesByOutput, family.chestBoatKey())) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.chestBoatKey(),
                1,
                "fallback_chest_boat",
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

---

## File: `src/main/java/com/splatage/wild_economy/catalog/classify/DefaultCategoryClassifier.java`

```java
package com.splatage.wild_economy.catalog.classify;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.util.Set;

public final class DefaultCategoryClassifier implements CategoryClassifier {

    private static final Set<String> FOOD_EXACT = Set.of(
        "apple",
        "bread",
        "cookie",
        "cake",
        "pumpkin_pie",
        "beetroot_soup",
        "mushroom_stew",
        "rabbit_stew",
        "suspicious_stew",
        "golden_apple",
        "enchanted_golden_apple",
        "dried_kelp",
        "honey_bottle"
    );

    private static final Set<String> FARMING_EXACT = Set.of(
        "wheat",
        "carrot",
        "potato",
        "beetroot",
        "melon_slice",
        "pumpkin",
        "melon",
        "sugar_cane",
        "bamboo",
        "cactus",
        "kelp",
        "sweet_berries",
        "glow_berries",
        "nether_wart"
    );

    private static final Set<String> MOB_DROP_EXACT = Set.of(
        "rotten_flesh",
        "bone",
        "arrow",
        "string",
        "spider_eye",
        "fermented_spider_eye",
        "gunpowder",
        "slime_ball",
        "magma_cream",
        "ender_pearl",
        "ghast_tear",
        "phantom_membrane",
        "feather",
        "leather",
        "ink_sac",
        "glow_ink_sac",
        "rabbit_hide",
        "prismarine_shard",
        "prismarine_crystals",
        "shulker_shell"
    );

    private static final Set<String> WOOD_FAMILY_PREFIXES = Set.of(
        "oak",
        "spruce",
        "birch",
        "jungle",
        "acacia",
        "dark_oak",
        "mangrove",
        "cherry",
        "pale_oak",
        "bamboo",
        "crimson",
        "warped"
    );

    @Override
    public CatalogCategory classify(final ItemFacts facts) {
        final String key = facts.key();

        if (isWood(key)) {
            return CatalogCategory.WOODS;
        }
        if (isStone(key)) {
            return CatalogCategory.STONE;
        }
        if (isOreOrMineral(key)) {
            return CatalogCategory.ORES_AND_MINERALS;
        }
        if (isFarming(key)) {
            return CatalogCategory.FARMING;
        }
        if (isFood(key, facts.edible())) {
            return CatalogCategory.FOOD;
        }
        if (isMobDrop(key)) {
            return CatalogCategory.MOB_DROPS;
        }
        if (isNether(key)) {
            return CatalogCategory.NETHER;
        }
        if (isEnd(key)) {
            return CatalogCategory.END;
        }
        if (isRedstone(key)) {
            return CatalogCategory.REDSTONE;
        }
        if (isBrewing(key)) {
            return CatalogCategory.BREWING;
        }
        if (isTool(key)) {
            return CatalogCategory.TOOLS;
        }
        if (isCombat(key)) {
            return CatalogCategory.COMBAT;
        }
        if (isTransport(key)) {
            return CatalogCategory.TRANSPORT;
        }
        if (isDecoration(key)) {
            return CatalogCategory.DECORATION;
        }

        return CatalogCategory.MISC;
    }

    private boolean isWood(final String key) {
        return key.endsWith("_log")
            || key.endsWith("_wood")
            || key.endsWith("_planks")
            || key.endsWith("_sapling")
            || key.endsWith("_leaves")
            || key.endsWith("_hanging_sign")
            || key.endsWith("_sign")
            || key.endsWith("_boat")
            || key.endsWith("_chest_boat")
            || key.endsWith("_raft")
            || key.endsWith("_chest_raft")
            || key.endsWith("_shelf")
            || key.contains("mangrove")
            || key.contains("bamboo_block")
            || key.contains("bamboo_planks")
            || key.contains("bamboo_mosaic")
            || key.contains("stripped_")
            || isWoodFamilyCrafted(key);
    }

    private boolean isWoodFamilyCrafted(final String key) {
        for (final String prefix : WOOD_FAMILY_PREFIXES) {
            if (!key.startsWith(prefix + "_")) {
                continue;
            }
            return key.endsWith("_button")
                || key.endsWith("_door")
                || key.endsWith("_fence")
                || key.endsWith("_fence_gate")
                || key.endsWith("_trapdoor")
                || key.endsWith("_pressure_plate")
                || key.endsWith("_slab")
                || key.endsWith("_stairs")
                || key.endsWith("_shelf");
        }
        return false;
    }

    private boolean isStone(final String key) {
        return key.contains("stone")
            || key.contains("cobble")
            || key.contains("deepslate")
            || key.contains("tuff")
            || key.contains("andesite")
            || key.contains("granite")
            || key.contains("diorite")
            || key.contains("blackstone")
            || key.contains("basalt")
            || key.contains("calcite")
            || key.contains("dripstone")
            || key.contains("brick")
            || key.equals("smooth_quartz")
            || key.equals("quartz_bricks");
    }

    private boolean isOreOrMineral(final String key) {
        return key.contains("_ore")
            || key.startsWith("raw_")
            || key.endsWith("_ingot")
            || key.endsWith("_nugget")
            || key.equals("coal")
            || key.equals("charcoal")
            || key.equals("diamond")
            || key.equals("emerald")
            || key.equals("lapis_lazuli")
            || key.equals("redstone")
            || key.equals("amethyst_shard")
            || key.equals("quartz")
            || key.equals("netherite_scrap")
            || key.equals("netherite_ingot")
            || key.endsWith("_block") && (
                key.startsWith("iron_")
                    || key.startsWith("gold_")
                    || key.startsWith("diamond_")
                    || key.startsWith("emerald_")
                    || key.startsWith("lapis_")
                    || key.startsWith("redstone_")
                    || key.startsWith("copper_")
                    || key.startsWith("coal_")
                    || key.startsWith("amethyst_")
                    || key.startsWith("netherite_")
            );
    }

    private boolean isFarming(final String key) {
        return FARMING_EXACT.contains(key)
            || key.endsWith("_seeds")
            || key.equals("wheat_seeds")
            || key.equals("beetroot_seeds")
            || key.equals("melon_seeds")
            || key.equals("pumpkin_seeds")
            || key.equals("torchflower_seeds")
            || key.equals("pitcher_pod")
            || key.contains("crop")
            || key.contains("farmland")
            || key.contains("cocoa")
            || key.contains("vine")
            || key.contains("propagule");
    }

    private boolean isFood(final String key, final boolean edible) {
        return edible
            || FOOD_EXACT.contains(key)
            || key.startsWith("cooked_")
            || key.startsWith("raw_")
            || key.endsWith("_meat")
            || key.endsWith("_stew")
            || key.endsWith("_slice")
            || key.endsWith("_berries")
            || key.equals("egg");
    }

    private boolean isMobDrop(final String key) {
        return MOB_DROP_EXACT.contains(key)
            || key.endsWith("_spawn_egg");
    }

    private boolean isNether(final String key) {
        return key.contains("nether")
            || key.contains("blaze")
            || key.contains("ghast")
            || key.contains("magma")
            || key.contains("netherrack")
            || key.contains("crimson")
            || key.contains("warped")
            || key.contains("soul_")
            || key.contains("blackstone")
            || key.contains("ancient_debris")
            || key.contains("shroomlight")
            || key.contains("glowstone");
    }

    private boolean isEnd(final String key) {
        return key.contains("end_")
            || key.contains("chorus")
            || key.contains("purpur")
            || key.contains("shulker")
            || key.contains("elytra")
            || key.contains("dragon_");
    }

    private boolean isRedstone(final String key) {
        return key.equals("redstone")
            || key.contains("repeater")
            || key.contains("comparator")
            || key.contains("observer")
            || key.contains("piston")
            || key.contains("hopper")
            || key.contains("dropper")
            || key.contains("dispenser")
            || key.contains("daylight_detector")
            || key.contains("target")
            || key.contains("lectern")
            || key.contains("tripwire")
            || key.contains("pressure_plate")
            || key.contains("redstone_lamp")
            || key.contains("sculk_sensor")
            || key.contains("calibrated_sculk_sensor");
    }

    private boolean isBrewing(final String key) {
        return key.contains("potion")
            || key.contains("brewing")
            || key.contains("cauldron")
            || key.contains("blaze_powder")
            || key.contains("ghast_tear")
            || key.contains("glistering_melon")
            || key.contains("nether_wart")
            || key.contains("dragon_breath")
            || key.contains("phantom_membrane");
    }

    private boolean isTool(final String key) {
        return key.endsWith("_pickaxe")
            || key.endsWith("_axe")
            || key.endsWith("_shovel")
            || key.endsWith("_hoe")
            || key.endsWith("_shears")
            || key.endsWith("_bucket")
            || key.equals("flint_and_steel")
            || key.equals("fishing_rod")
            || key.equals("carrot_on_a_stick")
            || key.equals("warped_fungus_on_a_stick")
            || key.equals("brush")
            || key.equals("spyglass")
            || key.equals("clock")
            || key.equals("compass")
            || key.equals("recovery_compass");
    }

    private boolean isCombat(final String key) {
        return key.endsWith("_sword")
            || key.endsWith("_helmet")
            || key.endsWith("_chestplate")
            || key.endsWith("_leggings")
            || key.endsWith("_boots")
            || key.equals("bow")
            || key.equals("crossbow")
            || key.equals("trident")
            || key.equals("shield")
            || key.equals("arrow")
            || key.equals("spectral_arrow")
            || key.equals("tipped_arrow")
            || key.contains("mace");
    }

    private boolean isTransport(final String key) {
        return key.contains("minecart")
            || key.contains("rail")
            || key.contains("boat")
            || key.contains("raft")
            || key.equals("saddle")
            || key.equals("lead");
    }

    private boolean isDecoration(final String key) {
        return key.contains("banner")
            || key.contains("bed")
            || key.contains("carpet")
            || key.contains("painting")
            || key.contains("flower_pot")
            || key.contains("candle")
            || key.contains("lantern")
            || key.contains("glass")
            || key.contains("terracotta")
            || key.contains("concrete")
            || key.contains("coral")
            || key.contains("pottery_sherd")
            || key.contains("head")
            || key.contains("skull")
            || key.contains("frame")
            || key.contains("amethyst_cluster")
            || key.contains("sea_pickle");
    }
}


```

