package com.splatage.wild_economy.testing.seed;

import com.splatage.wild_economy.testing.TestProfile;

public record SeedPlan(
        TestProfile profile,
        int playerCount,
        long randomSeed,
        int exchangeTransactionCount,
        int storePurchaseCount,
        int entitlementGrantCount,
        long nowEpochSecond,
        boolean resetFirst
) {
}
