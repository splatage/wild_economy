package com.splatage.wild_economy.gui.layout;

import java.util.List;

public record LayoutChildDefinition(
    String key,
    String label,
    int order,
    String icon,
    List<String> itemKeys,
    List<String> itemKeyPatterns
) {

    public LayoutChildDefinition {
        itemKeys = List.copyOf(itemKeys);
        itemKeyPatterns = List.copyOf(itemKeyPatterns);
    }
}
