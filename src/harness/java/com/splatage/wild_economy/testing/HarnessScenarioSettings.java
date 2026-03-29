package com.splatage.wild_economy.testing;

import com.splatage.wild_economy.testing.scenario.ScenarioMix;

public record HarnessScenarioSettings(
        int operations,
        int concurrency,
        ScenarioMix mix
) {
    public HarnessScenarioSettings {
        if (operations <= 0) {
            throw new IllegalArgumentException("scenario operations must be positive");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("scenario concurrency must be positive");
        }
        if (mix == null) {
            throw new IllegalArgumentException("scenario mix cannot be null");
        }
    }
}
