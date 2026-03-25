package com.splatage.wild_economy.economy.listener;

import com.splatage.wild_economy.economy.service.EconomyService;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class EconomyPlayerSessionListener implements Listener {

    private final EconomyService economyService;

    public EconomyPlayerSessionListener(final EconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        this.economyService.warmPlayerSession(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.economyService.flushPlayerSession(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
