package com.splatage.wild_economy.testing;

import java.util.Locale;

public enum HarnessMode {
    SEED_VERIFY,
    SCENARIOS,
    SEED_VERIFY_AND_SCENARIOS;

    public static HarnessMode parse(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Harness mode cannot be blank");
        }
        return HarnessMode.valueOf(rawValue.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public boolean includesSeed() {
        return this == SEED_VERIFY || this == SEED_VERIFY_AND_SCENARIOS;
    }

    public boolean includesScenarios() {
        return this == SCENARIOS || this == SEED_VERIFY_AND_SCENARIOS;
    }
}
