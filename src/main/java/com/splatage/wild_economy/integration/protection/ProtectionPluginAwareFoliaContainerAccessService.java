package com.splatage.wild_economy.integration.protection;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ProtectionPluginAwareFoliaContainerAccessService implements ContainerAccessService {

    private static final List<String> KNOWN_PROTECTION_PLUGINS = List.of(
        "GriefPrevention",
        "PlotSquared",
        "Towny",
        "Lands",
        "HuskClaims",
        "Residence",
        "Factions",
        "FactionsUUID",
        "SaberFactions",
        "MassiveCore",
        "KingdomsX"
    );

    private final String detectedProtectionPlugin;

    public ProtectionPluginAwareFoliaContainerAccessService() {
        this.detectedProtectionPlugin = this.detectProtectionPlugin();
    }

    @Override
    public ContainerAccessResult canAccessPlacedContainer(final Player player, final Block targetBlock) {
        if (this.detectedProtectionPlugin == null) {
            return ContainerAccessResult.allow();
        }

        return ContainerAccessResult.deny(
            "Placed container selling is temporarily disabled on Folia while protection plugin '"
                + this.detectedProtectionPlugin
                + "' is installed. Held shulker selling still works."
        );
    }

    private String detectProtectionPlugin() {
        for (final String pluginName : KNOWN_PROTECTION_PLUGINS) {
            if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                return pluginName;
            }
        }
        return null;
    }
}
