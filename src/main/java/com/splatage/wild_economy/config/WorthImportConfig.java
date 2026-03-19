package com.splatage.wild_economy.config;

public record WorthImportConfig(
    boolean enabled,
    String essentialsWorthFile,
    boolean useWorthAsBaseValue,
    boolean explicitItemConfigOverridesWorth,
    boolean ignoreMissingWorth
) {}
