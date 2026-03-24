package com.splatage.wild_economy.catalog.classify;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.rootvalue.CategoryHintLookup;
import java.util.Objects;
import java.util.Set;

public final class DefaultCategoryClassifier implements CategoryClassifier {

    private final CategoryHintLookup categoryHintLookup;

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


    public DefaultCategoryClassifier() {
        this(itemKey -> java.util.Optional.empty());
    }

    public DefaultCategoryClassifier(final CategoryHintLookup categoryHintLookup) {
        this.categoryHintLookup = Objects.requireNonNull(categoryHintLookup, "categoryHintLookup");
    }

    @Override
    public CatalogCategory classify(final ItemFacts facts) {
        final CatalogCategory categoryHint = this.categoryHintLookup.findCategoryHint(facts.key()).orElse(null);
        if (categoryHint != null) {
            return categoryHint;
        }

        final String key = facts.key();

        if (isWood(key)) {
            return CatalogCategory.WOODS;
        }
        if (isRedstone(key)) {
            return CatalogCategory.REDSTONE;
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

