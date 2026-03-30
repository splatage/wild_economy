package com.splatage.wild_economy.store.progress;

import java.util.Locale;
import org.bukkit.Material;

public final class StoreBuiltInCounters {

    public static final String BLOCKS_PLACED = "blocks_placed";
    public static final String BLOCKS_PLACED_SURVIVAL = "blocks_placed.survival";

    private StoreBuiltInCounters() {
    }

    public static String blocksPlacedMaterial(final Material material) {
        return BLOCKS_PLACED + "." + materialKey(material);
    }

    public static String blocksPlacedSurvivalMaterial(final Material material) {
        return BLOCKS_PLACED_SURVIVAL + "." + materialKey(material);
    }

    private static String materialKey(final Material material) {
        return material.getKey().getKey().toLowerCase(Locale.ROOT);
    }
}
