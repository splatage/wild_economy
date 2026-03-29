package com.splatage.wild_economy.catalog.model;

import org.bukkit.Material;

public record ItemFacts(
    Material material,
    String key,
    boolean isItem,
    boolean isBlock,
    boolean stackable,
    int maxStackSize,
    boolean edible,
    boolean fuelCandidate,
    boolean hasRootValue
) {
}
