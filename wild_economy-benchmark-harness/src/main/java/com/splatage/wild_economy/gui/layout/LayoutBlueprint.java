package com.splatage.wild_economy.gui.layout;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record LayoutBlueprint(Map<String, LayoutGroupDefinition> groups, Map<String, LayoutOverride> overrides) {

    public LayoutBlueprint {
        groups = Map.copyOf(groups);
        overrides = Map.copyOf(overrides);
    }

    public List<LayoutGroupDefinition> orderedGroups() {
        return this.groups.values().stream()
            .sorted(Comparator.comparingInt(LayoutGroupDefinition::order).thenComparing(LayoutGroupDefinition::key))
            .toList();
    }

    public Optional<LayoutGroupDefinition> group(final String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.groups.get(key));
    }

    public List<LayoutChildDefinition> orderedChildren(final String groupKey) {
        return this.group(groupKey)
            .map(LayoutGroupDefinition::orderedChildren)
            .orElse(List.of());
    }

    public Optional<LayoutOverride> override(final String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.overrides.get(itemKey));
    }
}
