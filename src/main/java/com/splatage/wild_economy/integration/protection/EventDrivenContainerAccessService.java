package com.splatage.wild_economy.integration.protection;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lockable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class EventDrivenContainerAccessService implements ContainerAccessService {

    private static final String PROTECTED_CONTAINER_MESSAGE = "You cannot use /shop sellcontainer on that protected container.";
    private static final String LOCKED_CONTAINER_MESSAGE = "That container is locked.";
    private static final String UNKNOWN_CONTAINER_MESSAGE = "Could not verify access to that container, so the sale was cancelled.";

    @Override
    public ContainerAccessResult canAccessPlacedContainer(
        final Player player,
        final Block targetBlock,
        final BlockState blockState
    ) {
        if (player == null || targetBlock == null || blockState == null) {
            return ContainerAccessResult.deny(UNKNOWN_CONTAINER_MESSAGE);
        }

        if (blockState instanceof Lockable lockable && lockable.isLocked()) {
            return ContainerAccessResult.deny(LOCKED_CONTAINER_MESSAGE);
        }

        final PlayerInteractEvent probe = new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_BLOCK,
            player.getInventory().getItemInMainHand(),
            targetBlock,
            BlockFace.UP,
            EquipmentSlot.HAND
        );

        try {
            Bukkit.getPluginManager().callEvent(probe);
        } catch (final Throwable ignored) {
            return ContainerAccessResult.deny(UNKNOWN_CONTAINER_MESSAGE);
        }

        if (this.isDenied(probe)) {
            return ContainerAccessResult.deny(PROTECTED_CONTAINER_MESSAGE);
        }

        return ContainerAccessResult.allow();
    }

    @SuppressWarnings("deprecation")
    private boolean isDenied(final PlayerInteractEvent probe) {
        return probe.useInteractedBlock() == Event.Result.DENY || probe.isCancelled();
    }
}
