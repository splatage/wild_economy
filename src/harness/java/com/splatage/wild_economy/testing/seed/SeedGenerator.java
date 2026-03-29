package com.splatage.wild_economy.testing.seed;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;

public interface SeedGenerator {
    SeedRunReport generate(HarnessBootstrap.HarnessComponents components, SeedPlan seedPlan);
}
