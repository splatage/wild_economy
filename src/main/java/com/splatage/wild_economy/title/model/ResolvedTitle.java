package com.splatage.wild_economy.title.model;

import java.util.Objects;

public record ResolvedTitle(
        String key,
        String text,
        TitleSource source,
        String family
) {
    public ResolvedTitle {
        key = Objects.requireNonNullElse(key, "");
        text = Objects.requireNonNullElse(text, "");
        Objects.requireNonNull(source, "source");
        family = Objects.requireNonNullElse(family, "");
    }

    public static ResolvedTitle empty() {
        return new ResolvedTitle("", "", TitleSource.EVENT, "");
    }
}
