package com.splatage.wild_economy.gui;

import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class TitleMenuListener implements Listener {

    private final TitleMenu titleMenu;

    public TitleMenuListener(final TitleMenu titleMenu) {
        this.titleMenu = Objects.requireNonNull(titleMenu, "titleMenu");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TitleMenuHolder holder)) {
            return;
        }
        this.titleMenu.handleClick(event, holder.page());
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TitleMenuHolder) {
            event.setCancelled(true);
        }
    }
}
