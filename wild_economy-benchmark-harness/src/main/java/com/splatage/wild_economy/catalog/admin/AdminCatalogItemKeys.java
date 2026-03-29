package com.splatage.wild_economy.catalog.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdminCatalogItemKeys {

    private static final String MINECRAFT_PREFIX = "minecraft:";

    private AdminCatalogItemKeys() {
    }

    public static String canonicalize(final String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(MINECRAFT_PREFIX)) {
            normalized = normalized.substring(MINECRAFT_PREFIX.length());
        }
        return normalized;
    }

    public static List<String> canonicalizeAll(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        final List<String> normalized = new ArrayList<>(values.size());
        for (final String value : values) {
            final String canonical = canonicalize(value);
            if (!canonical.isBlank()) {
                normalized.add(canonical);
            }
        }
        return List.copyOf(normalized);
    }
}

