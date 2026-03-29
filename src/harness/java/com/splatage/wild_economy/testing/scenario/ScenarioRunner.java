package com.splatage.wild_economy.testing.scenario;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.testing.seed.SeedPlan;

public interface ScenarioRunner {

    ScenarioRunReport run(
            HarnessBootstrap.HarnessComponents components,
            SeedPlan seedPlan,
            int operations,
            int concurrency,
            long durationSeconds,
            ScenarioMix mix
    );
}
