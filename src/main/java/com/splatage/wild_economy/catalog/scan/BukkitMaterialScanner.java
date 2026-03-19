package com.splatage.wild_economy.catalog.scan;

import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.worth.WorthPriceLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;

public final class BukkitMaterialScanner implements MaterialScanner {

    private final WorthPriceLookup worthPriceLookup;

    public BukkitMaterialScanner(final WorthPriceLookup worthPriceLookup) {
        this.worthPriceLookup = worthPriceLookup;
    }

    @Override
    public List<ItemFacts> scanAll() {
        final List<ItemFacts> results = new ArrayList<>();

        for (final Material material : Material.values()) {
            if (!isIncludedMaterial(material)) {
                continue;
            }

            final String key = normalizeKey(material);
            final boolean hasWorthEntry = this.worthPriceLookup.findPrice(key).isPresent();

            results.add(new ItemFacts(
                material,
                key,
                material.isItem(),
                material.isBlock(),
                material.getMaxStackSize() > 1,
                material.getMaxStackSize(),
                material.isEdible(),
                material.isFuel(),
                hasWorthEntry
            ));
        }

        results.sort(Comparator.comparing(ItemFacts::key));
        return List.copyOf(results);
    }

    private boolean isIncludedMaterial(final Material material) {
        if (material == Material.AIR) {
            return false;
        }
        if (!material.isItem()) {
            return false;
        }
        return !material.isLegacy();
    }

    public static String normalizeKey(final Material material) {
        return material.name().toLowerCase(java.util.Locale.ROOT);
    }
}
