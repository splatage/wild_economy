package com.splatage.wild_economy.catalog.classify;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.policy.CatalogSafetyExclusions;
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

    public DefaultCategoryClassifier() {
    }

    @Override
    public CatalogCategory classify(final ItemFacts facts) {
        final String key = facts.key();

        if (CatalogSafetyExclusions.isHardDisabled(key)) {
            return CatalogCategory.MISC;
        }
        if (isFood(key, facts.edible())) {
            return CatalogCategory.FOOD;
        }
        if (isFarming(key)) {
            return CatalogCategory.FARMING;
        }
        if (isMobDrop(key)) {
            return CatalogCategory.MOB_DROPS;
        }
        if (isOreOrMineral(key)) {
            return CatalogCategory.ORES_AND_MINERALS;
        }
        if (isRedstone(key)) {
            return CatalogCategory.REDSTONE;
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
        if (isBrewing(key)) {
            return CatalogCategory.BREWING;
        }
        if (isNether(key)) {
            return CatalogCategory.NETHER;
        }
        if (isEnd(key)) {
            return CatalogCategory.END;
        }
        if (isWood(key)) {
            return CatalogCategory.WOODS;
        }
        if (isStone(key)) {
            return CatalogCategory.STONE;
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
            || key.endsWith("_stem")
            || key.endsWith("_hyphae")
            || key.startsWith("stripped_");
    }

    private boolean isStone(final String key) {
        return key.equals("stone")
            || key.equals("cobblestone")
            || key.equals("smooth_stone")
            || key.endsWith("_stone")
            || key.equals("deepslate")
            || key.equals("cobbled_deepslate")
            || key.equals("tuff")
            || key.equals("andesite")
            || key.equals("granite")
            || key.equals("diorite")
            || key.equals("blackstone")
            || key.equals("basalt")
            || key.equals("calcite")
            || key.equals("dripstone_block");
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
            || key.equals("netherite_ingot");
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
            || key.endsWith("_sapling")
            || key.endsWith("_propagule")
            || key.contains("cocoa")
            || key.equals("vine");
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
        return MOB_DROP_EXACT.contains(key);
    }

    private boolean isNether(final String key) {
        return key.contains("nether")
            || key.contains("blaze")
            || key.contains("ghast")
            || key.contains("magma")
            || key.contains("netherrack")
            || key.contains("crimson")
            || key.contains("warped")
            || key.contains("soul_");
    }

    private boolean isEnd(final String key) {
        return key.contains("end_")
            || key.contains("chorus")
            || key.contains("purpur")
            || key.contains("shulker")
            || key.contains("dragon")
            || key.equals("elytra");
    }

    private boolean isBrewing(final String key) {
        return key.contains("potion")
            || key.contains("brewing")
            || key.contains("cauldron")
            || key.contains("glistering_melon")
            || key.contains("blaze_powder")
            || key.contains("blaze_rod")
            || key.contains("nether_wart")
            || key.contains("ghast_tear")
            || key.contains("rabbit_foot")
            || key.contains("phantom_membrane")
            || key.contains("dragon_breath")
            || key.contains("magma_cream");
    }

    private boolean isTool(final String key) {
        return key.endsWith("_pickaxe")
            || key.endsWith("_axe")
            || key.endsWith("_shovel")
            || key.endsWith("_hoe")
            || key.endsWith("_shears")
            || key.endsWith("_brush")
            || key.endsWith("_flint_and_steel")
            || key.endsWith("_fishing_rod")
            || key.endsWith("_spyglass")
            || key.endsWith("_clock")
            || key.endsWith("_compass")
            || key.equals("recovery_compass");
    }

    private boolean isCombat(final String key) {
        return key.endsWith("_sword")
            || key.endsWith("_helmet")
            || key.endsWith("_chestplate")
            || key.endsWith("_leggings")
            || key.endsWith("_boots")
            || key.equals("shield")
            || key.equals("bow")
            || key.equals("crossbow")
            || key.equals("trident")
            || key.equals("mace")
            || key.equals("totem_of_undying");
    }

    private boolean isTransport(final String key) {
        return key.endsWith("_boat")
            || key.endsWith("_chest_boat")
            || key.endsWith("_raft")
            || key.endsWith("_chest_raft")
            || key.contains("minecart")
            || key.equals("saddle")
            || key.equals("elytra")
            || key.equals("carrot_on_a_stick")
            || key.equals("warped_fungus_on_a_stick");
    }

    private boolean isDecoration(final String key) {
        return key.endsWith("_sign")
            || key.endsWith("_hanging_sign")
            || key.endsWith("_banner")
            || key.equals("painting")
            || key.equals("item_frame")
            || key.equals("glow_item_frame")
            || key.equals("flower_pot")
            || key.endsWith("_candle")
            || key.endsWith("_carpet");
    }

    private boolean isRedstone(final String key) {
        return key.equals("redstone")
            || key.equals("repeater")
            || key.equals("comparator")
            || key.equals("observer")
            || key.equals("hopper")
            || key.equals("dispenser")
            || key.equals("dropper")
            || key.equals("piston")
            || key.equals("sticky_piston")
            || key.equals("lever")
            || key.endsWith("_button")
            || key.endsWith("_pressure_plate")
            || key.equals("daylight_detector")
            || key.equals("tripwire_hook")
            || key.equals("target")
            || key.equals("lightning_rod")
            || key.equals("sculk_sensor")
            || key.equals("calibrated_sculk_sensor");
    }
}
