package com.splatage.wild_economy.gui.layout;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public List<LayoutChildDefinition> orderedChildren() {
        return this.children.values().stream()
            .sorted(Comparator.comparingInt(LayoutChildDefinition::order).thenComparing(LayoutChildDefinition::key))
            .toList();
    }

    public Optional<LayoutChildDefinition> child(final String childKey) {
        if (childKey == null || childKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.children.get(childKey));
    }
}
