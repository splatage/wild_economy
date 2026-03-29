package com.splatage.wild_economy.testing.scenario;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.testing.seed.SeedPlan;
import java.util.List;

public interface ScenarioRunner {

    List<ScenarioResult> run(
            HarnessBootstrap.HarnessComponents components,
            SeedPlan seedPlan,
            int operations,
            int concurrency,
            ScenarioMix mix
    );
}
