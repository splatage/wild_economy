package com.splatage.wild_economy.catalog.derive;

public enum DerivationReason {
    ROOT_ANCHOR,
    DERIVED_FROM_ROOT,
    DEPTH_LIMIT,
    ALL_PATHS_BLOCKED,
    NO_RECIPE_AND_NO_ROOT,
    CYCLE_DETECTED,
    HARD_DISABLED
}
