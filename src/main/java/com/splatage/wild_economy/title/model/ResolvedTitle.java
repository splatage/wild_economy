package com.splatage.wild_economy.title.model;

import java.util.Objects;

public record ResolvedTitle(
    String key,
    String renderedText,
    TitleSource source,
    String family,
    Integer tier
) {
    public static final ResolvedTitle EMPTY = new ResolvedTitle("", "", TitleSource.EVENT, null, null);

    public ResolvedTitle {
        key = Objects.requireNonNull(key, "key");
        renderedText = Objects.requireNonNull(renderedText, "renderedText");
        source = Objects.requireNonNull(source, "source");
    }

    public boolean isEmpty() {
        return this.renderedText.isBlank();
    }
}
