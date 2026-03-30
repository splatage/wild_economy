package com.splatage.wild_economy.store.listener;

import com.splatage.wild_economy.store.progress.StoreBuiltInCounters;
import com.splatage.wild_economy.store.progress.StoreProgressService;
import java.util.Objects;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class StoreProgressListener implements Listener {

    private final StoreProgressService storeProgressService;

    public StoreProgressListener(final StoreProgressService storeProgressService) {
        this.storeProgressService = Objects.requireNonNull(storeProgressService, "storeProgressService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        final long placedCount = event instanceof BlockMultiPlaceEvent multiPlaceEvent
            ? multiPlaceEvent.getReplacedBlockStates().size()
            : 1L;

        this.storeProgressService.incrementCustomCounter(player, StoreBuiltInCounters.BLOCKS_PLACED, placedCount);
        this.storeProgressService.incrementCustomCounter(player, StoreBuiltInCounters.blocksPlacedMaterial(event.getBlockPlaced().getType()), placedCount);

        if (player.getGameMode() == GameMode.SURVIVAL) {
            this.storeProgressService.incrementCustomCounter(player, StoreBuiltInCounters.BLOCKS_PLACED_SURVIVAL, placedCount);
            this.storeProgressService.incrementCustomCounter(player, StoreBuiltInCounters.blocksPlacedSurvivalMaterial(event.getBlockPlaced().getType()), placedCount);
        }
    }
}
