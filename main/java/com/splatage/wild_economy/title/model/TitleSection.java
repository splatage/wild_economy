package com.splatage.wild_economy.title.model;

import java.util.Locale;

public enum TitleSection {
    RELIC("Relic Titles"),
    BEST_OF_ALL_TIME("Best of All Time"),
    ACHIEVEMENT("Achievement Titles"),
    AUTHORITY("Authority Titles"),
    TIME_ON_SERVER("Time on Server");

    private final String displayName;

    TitleSection(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }

    public static TitleSection fromConfig(final String rawValue) {
        return TitleSection.valueOf(rawValue.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
    }
}
