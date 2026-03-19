package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;

public final class PluginBootstrap {

    private final WildEconomyPlugin plugin;
    private ServiceRegistry services;

    public PluginBootstrap(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.services = new ServiceRegistry(this.plugin);
        this.services.initialize();
        this.services.registerCommands();
        this.services.registerTasks();
    }

    public void disable() {
        if (this.services != null) {
            this.services.shutdown();
        }
    }
}
