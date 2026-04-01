package com.splatage.wild_economy.title.model;

import java.util.Objects;

public record PlayerTitleSelection(
    TitleDisplayMode displayMode,
    String selectedTitleKey
) {
    public PlayerTitleSelection {
        displayMode = Objects.requireNonNull(displayMode, "displayMode");
        selectedTitleKey = normalizeNullable(selectedTitleKey);
    }

    public static PlayerTitleSelection manual(final String selectedTitleKey) {
        return new PlayerTitleSelection(TitleDisplayMode.MANUAL, selectedTitleKey);
    }

    public static PlayerTitleSelection autoHighestPriority() {
        return new PlayerTitleSelection(TitleDisplayMode.AUTO_HIGHEST_PRIORITY, null);
    }

    private static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
