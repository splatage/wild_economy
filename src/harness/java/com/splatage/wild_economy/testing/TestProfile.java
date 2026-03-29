package com.splatage.wild_economy.testing;

import java.util.Locale;

public enum TestProfile {
    SMOKE,
    QA,
    PERF,
    SOAK;

    public static TestProfile parse(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Profile cannot be blank");
        }
        return TestProfile.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
    }
}
