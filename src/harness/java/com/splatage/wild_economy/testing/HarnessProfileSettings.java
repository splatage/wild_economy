package com.splatage.wild_economy.testing;

public record HarnessProfileSettings(
        int playerCount,
        long randomSeed,
        int exchangeTransactionCount,
        int storePurchaseCount,
        int entitlementGrantCount,
        HarnessScenarioSettings scenario
) {
    public HarnessProfileSettings {
        if (playerCount <= 0) {
            throw new IllegalArgumentException("playerCount must be positive");
        }
        if (exchangeTransactionCount < 0) {
            throw new IllegalArgumentException("exchangeTransactionCount cannot be negative");
        }
        if (storePurchaseCount < 0) {
            throw new IllegalArgumentException("storePurchaseCount cannot be negative");
        }
        if (entitlementGrantCount < 0) {
            throw new IllegalArgumentException("entitlementGrantCount cannot be negative");
        }
        if (scenario == null) {
            throw new IllegalArgumentException("scenario settings cannot be null");
        }
    }
}
