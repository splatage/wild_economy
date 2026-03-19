package com.splatage.wild_economy.config;

import com.splatage.wild_economy.WildEconomyPlugin;

public final class ConfigLoader {

    private final WildEconomyPlugin plugin;

    public ConfigLoader(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public GlobalConfig loadGlobalConfig() {
        return new GlobalConfig(72000L, 45, "shop", "shopadmin", false);
    }
}
