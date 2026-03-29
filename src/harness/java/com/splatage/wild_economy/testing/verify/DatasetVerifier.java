package com.splatage.wild_economy.testing.verify;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.testing.seed.SeedPlan;

public interface DatasetVerifier {
    InvariantReport verify(HarnessBootstrap.HarnessComponents components, SeedPlan seedPlan);
}
