package com.splatage.wild_economy.gui;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class TitleMenuHolder implements InventoryHolder {

    private final int page;
    private Inventory inventory;

    private TitleMenuHolder(final int page) {
        this.page = Math.max(0, page);
    }

    public static TitleMenuHolder page(final int page) {
        return new TitleMenuHolder(page);
    }

    public int page() {
        return this.page;
    }

    public Inventory createInventory(final int size, final String title) {
        final Inventory created = Bukkit.createInventory(this, size, Objects.requireNonNull(title, "title"));
        this.inventory = created;
        return created;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
