package com.splatage.wild_economy.integration.protection;

import com.splatage.wild_economy.platform.PlatformSupport;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ContainerAccessServices {

    private static final String GRIEF_PREVENTION_PLUGIN = "GriefPrevention";

    private ContainerAccessServices() {
    }

    public static ContainerAccessService createDefault() {
        if (!PlatformSupport.isFolia()) {
            return new EventDrivenContainerAccessService();
        }

        final Plugin griefPrevention = Bukkit.getPluginManager().getPlugin(GRIEF_PREVENTION_PLUGIN);
        if (griefPrevention != null && griefPrevention.isEnabled()) {
            return new GriefPreventionContainerAccessService(griefPrevention);
        }

        return new ProtectionPluginAwareFoliaContainerAccessService();
    }
}
