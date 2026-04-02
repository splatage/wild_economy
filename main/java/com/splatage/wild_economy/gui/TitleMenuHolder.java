package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.title.model.TitleSection;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class TitleMenuHolder implements InventoryHolder {

    public enum View {
        HUB,
        SECTION,
        FAMILY
    }

    private final View view;
    private final int page;
    private final TitleSection section;
    private final String family;
    private Inventory inventory;

    private TitleMenuHolder(final View view, final int page, final TitleSection section, final String family) {
        this.view = Objects.requireNonNull(view, "view");
        this.page = Math.max(0, page);
        this.section = section;
        this.family = family;
    }

    public static TitleMenuHolder hub() {
        return new TitleMenuHolder(View.HUB, 0, null, null);
    }

    public static TitleMenuHolder section(final TitleSection section, final int page) {
        return new TitleMenuHolder(View.SECTION, page, Objects.requireNonNull(section, "section"), null);
    }

    public static TitleMenuHolder family(final TitleSection section, final String family, final int page) {
        return new TitleMenuHolder(View.FAMILY, page, Objects.requireNonNull(section, "section"), Objects.requireNonNull(family, "family"));
    }

    public View view() {
        return this.view;
    }

    public int page() {
        return this.page;
    }

    public TitleSection section() {
        return this.section;
    }

    public String family() {
        return this.family;
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
