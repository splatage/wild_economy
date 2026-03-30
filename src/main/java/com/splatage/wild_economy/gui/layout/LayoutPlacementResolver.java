package com.splatage.wild_economy.gui.layout;

import com.splatage.wild_economy.catalog.rootvalue.RootValueLoader;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.item.ExchangeItemCodec;
import java.util.Objects;

public final class LayoutPlacementResolver {

    private final LayoutBlueprint blueprint;
    private final ExchangeItemCodec exchangeItemCodec;

    public LayoutPlacementResolver(final LayoutBlueprint blueprint) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.exchangeItemCodec = new ExchangeItemCodec();
    }

    public LayoutPlacement resolve(final ItemKey itemKey) {
        return this.resolve(itemKey.value());
    }

    public LayoutPlacement resolve(final String rawItemKey) {
        final String itemKey = RootValueLoader.normalizeKey(rawItemKey);
        if (itemKey.isBlank()) {
            return fallbackPlacement();
        }

        final LayoutPlacement exact = this.resolveExact(itemKey);
        if (exact != null) {
            return exact;
        }

        return this.exchangeItemCodec.baseCatalogKey(new ItemKey(itemKey))
            .map(ItemKey::value)
            .map(this::resolveExact)
            .orElseGet(this::fallbackPlacement);
    }

    private LayoutPlacement resolveExact(final String itemKey) {
        final LayoutOverride override = this.blueprint.override(itemKey).orElse(null);
        if (override != null && override.group() != null && !override.group().isBlank()) {
            return new LayoutPlacement(override.group(), override.child());
        }

        for (final LayoutGroupDefinition group : this.blueprint.orderedGroups()) {
            for (final LayoutChildDefinition child : group.orderedChildren()) {
                if (matches(child.itemKeys(), child.itemKeyPatterns(), itemKey)) {
                    return new LayoutPlacement(group.key(), child.key());
                }
            }

            if (matches(group.itemKeys(), group.itemKeyPatterns(), itemKey)) {
                return new LayoutPlacement(group.key(), null);
            }
        }

        return null;
    }

    private static boolean matches(final java.util.List<String> itemKeys, final java.util.List<String> itemKeyPatterns, final String itemKey) {
        if (itemKeys.contains(itemKey)) {
            return true;
        }
        for (final String pattern : itemKeyPatterns) {
            if (wildcardMatches(pattern, itemKey)) {
                return true;
            }
        }
        return false;
    }

    private LayoutPlacement fallbackPlacement() {
        if (this.blueprint.group("MISC").isPresent()) {
            final LayoutGroupDefinition misc = this.blueprint.group("MISC").orElseThrow();
            if (misc.child("OTHER").isPresent()) {
                return new LayoutPlacement("MISC", "OTHER");
            }
            return new LayoutPlacement("MISC", null);
        }
        final LayoutGroupDefinition first = this.blueprint.orderedGroups().stream().findFirst().orElse(null);
        if (first == null) {
            return new LayoutPlacement(null, null);
        }
        final LayoutChildDefinition firstChild = first.orderedChildren().stream().findFirst().orElse(null);
        return new LayoutPlacement(first.key(), firstChild == null ? null : firstChild.key());
    }

    private static boolean wildcardMatches(final String wildcardPattern, final String value) {
        final StringBuilder regex = new StringBuilder("^");
        for (final char c : wildcardPattern.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '$', '^', '+', '|', '\\' -> regex.append('\\').append(c);
                default -> regex.append(c);
            }
        }
        regex.append('$');
        return value.matches(regex.toString());
    }
}
