package com.splatage.wild_economy;

import com.splatage.wild_economy.bootstrap.PluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

public final class WildEconomyPlugin extends JavaPlugin {

    private PluginBootstrap bootstrap;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.saveResource("database.yml", false);
        this.saveResource("exchange-items.yml", false);
        this.saveResource("worth-import.yml", false);
        this.saveResource("messages.yml", false);

        this.bootstrap = new PluginBootstrap(this);
        this.bootstrap.enable();
    }

    public PluginBootstrap getBootstrap() {
        return this.bootstrap;
    }

    @Override
    public void onDisable() {
        if (this.bootstrap != null) {
            this.bootstrap.disable();
        }
    }
}
