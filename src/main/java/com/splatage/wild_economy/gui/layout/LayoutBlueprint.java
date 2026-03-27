package com.splatage.wild_economy.gui.layout;

import java.util.List;
import java.util.Map;

public record LayoutBlueprint(Map<String, LayoutGroupDefinition> groups, Map<String, LayoutOverride> overrides) {

    public LayoutBlueprint {
        groups = Map.copyOf(groups);
        overrides = Map.copyOf(overrides);
    }

    public List<LayoutGroupDefinition> orderedGroups() {
        return this.groups.values().stream()
            .sorted((left, right) -> Integer.compare(left.order(), right.order()))
            .toList();
    }
}
