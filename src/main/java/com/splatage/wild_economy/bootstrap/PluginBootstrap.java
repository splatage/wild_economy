package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;

public final class PluginBootstrap {

    private final WildEconomyPlugin plugin;
    private final Object lifecycleLock = new Object();

    private ServiceRegistry services;

    public PluginBootstrap(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        synchronized (this.lifecycleLock) {
            if (this.services != null) {
                return;
            }
            this.services = this.startServices();
        }
    }

    public void reload() {
        synchronized (this.lifecycleLock) {
            final ServiceRegistry current = this.services;
            this.services = null;

            if (current != null) {
                current.shutdown();
            }

            this.services = this.startServices();
        }
    }

    public void disable() {
        synchronized (this.lifecycleLock) {
            final ServiceRegistry current = this.services;
            this.services = null;

            if (current != null) {
                current.shutdown();
            }
        }
    }

    private ServiceRegistry startServices() {
        final ServiceRegistry registry = new ServiceRegistry(this.plugin);
        registry.initialize();
        registry.registerCommands();
        registry.registerTasks();
        return registry;
    }
}
