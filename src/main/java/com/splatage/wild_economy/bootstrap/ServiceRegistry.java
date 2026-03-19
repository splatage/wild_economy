package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Load configs
        // Initialize database provider
        // Run migrations
        // Build catalog
        // Create services
    }

    public void registerCommands() {
        // Register /shop and /shopadmin executors
    }

    public void registerTasks() {
        // Register turnover scheduler
    }

    public void shutdown() {
        // Close pools/executors if needed
    }
}
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import org.bukkit.command.PluginCommand;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Load configs
        // Initialize database provider
        // Run migrations
        // Build catalog
        // Create services
    }

    public void registerCommands() {
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand());
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand());
        }
    }

    public void registerTasks() {
        // Register turnover scheduler
    }

    public void shutdown() {
        // Close pools/executors if needed
    }
}
