package com.splatage.wild_economy.gui.layout;

import java.util.List;
import java.util.Map;

public record LayoutGroupDefinition(
    String key,
    String label,
    int order,
    String icon,
    Map<String, LayoutChildDefinition> children,
    List<String> itemKeys,
    List<String> itemKeyPatterns
) {

    public LayoutGroupDefinition {
        children = Map.copyOf(children);
        itemKeys = List.copyOf(itemKeys);
        itemKeyPatterns = List.copyOf(itemKeyPatterns);
    }
}
