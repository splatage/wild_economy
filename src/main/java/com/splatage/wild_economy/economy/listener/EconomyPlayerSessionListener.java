package com.splatage.wild_economy.economy.listener;

import com.splatage.wild_economy.economy.service.EconomyService;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class EconomyPlayerSessionListener implements Listener {

    private final Plugin plugin;
    private final EconomyService economyService;

    public EconomyPlayerSessionListener(final Plugin plugin, final EconomyService economyService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        final String playerName = event.getPlayer().getName();
        this.plugin.getServer().getAsyncScheduler().runNow(this.plugin, task ->
                this.economyService.warmPlayerSession(playerId, playerName));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        final String playerName = event.getPlayer().getName();
        this.plugin.getServer().getAsyncScheduler().runNow(this.plugin, task ->
                this.economyService.flushPlayerSession(playerId, playerName));
    }
}
