package com.splatage.wild_economy.title.model;

import com.splatage.wild_economy.store.model.StoreAccessControlled;
import com.splatage.wild_economy.store.model.StoreRequirement;
import com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record TitleOption(
    String key,
    String displayName,
    String titleText,
    String icon,
    Integer slot,
    TitleSource source,
    String family,
    Integer tier,
    int priority,
    List<String> lore,
    List<StoreRequirement> requirements,
    StoreVisibilityWhenUnmet visibilityWhenUnmet,
    String lockedMessage
) implements StoreAccessControlled {

    public static final Comparator<TitleOption> DISPLAY_ORDER =
        Comparator.comparing(TitleOption::source)
            .thenComparing(TitleOption::priority, Comparator.reverseOrder())
            .thenComparing(option -> option.slot() == null ? Integer.MAX_VALUE : option.slot())
            .thenComparing(TitleOption::displayName);

    public TitleOption {
        key = requireText(key, "key");
        displayName = requireText(displayName, "displayName");
        titleText = requireText(titleText, "titleText");
        icon = requireText(icon, "icon");
        source = Objects.requireNonNull(source, "source");
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        visibilityWhenUnmet = Objects.requireNonNull(visibilityWhenUnmet, "visibilityWhenUnmet");
        family = normalizeNullable(family);
        if (tier != null && tier.intValue() < 0) {
            throw new IllegalArgumentException("tier cannot be negative");
        }
    }

    private static String requireText(final String value, final String fieldName) {
        final String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }

    private static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
