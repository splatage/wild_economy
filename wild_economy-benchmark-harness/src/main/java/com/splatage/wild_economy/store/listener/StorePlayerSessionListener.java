package com.splatage.wild_economy.store.listener;

import com.splatage.wild_economy.store.state.StoreRuntimeStateService;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StorePlayerSessionListener implements Listener {

    private final StoreRuntimeStateService storeRuntimeStateService;

    public StorePlayerSessionListener(final StoreRuntimeStateService storeRuntimeStateService) {
        this.storeRuntimeStateService = Objects.requireNonNull(storeRuntimeStateService, "storeRuntimeStateService");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.storeRuntimeStateService.handlePlayerQuit(event.getPlayer().getUniqueId());
    }
}
