package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopMenuHolder implements InventoryHolder {

    public enum ViewType {
        ROOT,
        SUBCATEGORY,
        BROWSE,
        DETAIL
    }

    private final ViewType viewType;
    private final ItemCategory currentCategory;
    private final GeneratedItemCategory currentGeneratedCategory;
    private final int currentPage;
    private final ItemKey currentItemKey;
    private final boolean viaSubcategory;
    private final BigDecimal quotedUnitPrice;
    private final long quotedAtMillis;

    private Inventory inventory;

    private ShopMenuHolder(
        final ViewType viewType,
        final ItemCategory currentCategory,
        final GeneratedItemCategory currentGeneratedCategory,
        final int currentPage,
        final ItemKey currentItemKey,
        final boolean viaSubcategory,
        final BigDecimal quotedUnitPrice,
        final long quotedAtMillis
    ) {
        this.viewType = Objects.requireNonNull(viewType, "viewType");
        this.currentCategory = currentCategory;
        this.currentGeneratedCategory = currentGeneratedCategory;
        this.currentPage = currentPage;
        this.currentItemKey = currentItemKey;
        this.viaSubcategory = viaSubcategory;
        this.quotedUnitPrice = quotedUnitPrice;
        this.quotedAtMillis = quotedAtMillis;
    }

    public static ShopMenuHolder root() {
        return new ShopMenuHolder(ViewType.ROOT, null, null, 0, null, false, null, 0L);
    }

    public static ShopMenuHolder subcategory(final ItemCategory category) {
        return new ShopMenuHolder(
            ViewType.SUBCATEGORY,
            Objects.requireNonNull(category, "category"),
            null,
            0,
            null,
            false,
            null,
            0L
        );
    }

    public static ShopMenuHolder browse(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        return new ShopMenuHolder(
            ViewType.BROWSE,
            Objects.requireNonNull(category, "category"),
            generatedCategory,
            page,
            null,
            viaSubcategory,
            null,
            0L
        );
    }

    public static ShopMenuHolder detail(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final ItemKey itemKey,
        final boolean viaSubcategory
    ) {
        return detail(category, generatedCategory, page, itemKey, viaSubcategory, null, 0L);
    }

    public static ShopMenuHolder detail(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final ItemKey itemKey,
        final boolean viaSubcategory,
        final BigDecimal quotedUnitPrice
    ) {
        return detail(category, generatedCategory, page, itemKey, viaSubcategory, quotedUnitPrice, 0L);
    }

    public static ShopMenuHolder detail(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final ItemKey itemKey,
        final boolean viaSubcategory,
        final BigDecimal quotedUnitPrice,
        final long quotedAtMillis
    ) {
        return new ShopMenuHolder(
            ViewType.DETAIL,
            category,
            generatedCategory,
            page,
            Objects.requireNonNull(itemKey, "itemKey"),
            viaSubcategory,
            quotedUnitPrice,
            quotedAtMillis
        );
    }

    public Inventory createInventory(final int size, final String title) {
        final Inventory created = Bukkit.createInventory(this, size, title);
        this.inventory = created;
        return created;
    }

    @Override
    public Inventory getInventory() {
        if (this.inventory == null) {
            throw new IllegalStateException("Inventory has not been created for this holder yet");
        }
        return this.inventory;
    }

    public ViewType viewType() {
        return this.viewType;
    }

    public ItemCategory currentCategory() {
        return this.currentCategory;
    }

    public GeneratedItemCategory currentGeneratedCategory() {
        return this.currentGeneratedCategory;
    }

    public int currentPage() {
        return this.currentPage;
    }

    public ItemKey currentItemKey() {
        return this.currentItemKey;
    }

    public boolean viaSubcategory() {
        return this.viaSubcategory;
    }

    public BigDecimal quotedUnitPrice() {
        return this.quotedUnitPrice;
    }

    public long quotedAtMillis() {
        return this.quotedAtMillis;
    }
}
