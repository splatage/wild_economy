package com.splatage.wild_economy.title.model;

import com.splatage.wild_economy.store.model.StoreRequirement;
import com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet;
import java.util.List;
import java.util.Objects;

public record TitleOption(
        String key,
        String displayName,
        String titleText,
        String icon,
        Integer slot,
        TitleSource source,
        TitleSection section,
        String family,
        Integer tier,
        int priority,
        List<String> lore,
        List<StoreRequirement> requirements,
        StoreVisibilityWhenUnmet visibilityWhenUnmet,
        String lockedMessage
) {
    public TitleOption {
        key = requireNonBlank(key, "key");
        displayName = requireNonBlank(displayName, "displayName");
        titleText = requireNonBlank(titleText, "titleText");
        icon = requireNonBlank(icon, "icon");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(section, "section");
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        Objects.requireNonNull(visibilityWhenUnmet, "visibilityWhenUnmet");
    }

    private static String requireNonBlank(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
