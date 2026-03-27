package com.splatage.wild_economy.gui;

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
        DETAIL,
        STORE_ROOT,
        STORE_CATEGORY,
        STORE_DETAIL,
        STORE_XP_BOTTLES
    }

    private final ViewType viewType;
    private final String currentLayoutGroupKey;
    private final String currentLayoutChildKey;
    private final int currentPage;
    private final ItemKey currentItemKey;
    private final boolean viaSubcategory;
    private final BigDecimal quotedUnitPrice;
    private final long quotedAtMillis;
    private final String currentStoreCategoryId;
    private final String currentStoreProductId;

    private Inventory inventory;

    private ShopMenuHolder(
        final ViewType viewType,
        final String currentLayoutGroupKey,
        final String currentLayoutChildKey,
        final int currentPage,
        final ItemKey currentItemKey,
        final boolean viaSubcategory,
        final BigDecimal quotedUnitPrice,
        final long quotedAtMillis,
        final String currentStoreCategoryId,
        final String currentStoreProductId
    ) {
        this.viewType = Objects.requireNonNull(viewType, "viewType");
        this.currentLayoutGroupKey = currentLayoutGroupKey;
        this.currentLayoutChildKey = currentLayoutChildKey;
        this.currentPage = currentPage;
        this.currentItemKey = currentItemKey;
        this.viaSubcategory = viaSubcategory;
        this.quotedUnitPrice = quotedUnitPrice;
        this.quotedAtMillis = quotedAtMillis;
        this.currentStoreCategoryId = currentStoreCategoryId;
        this.currentStoreProductId = currentStoreProductId;
    }

    public static ShopMenuHolder root() {
        return new ShopMenuHolder(ViewType.ROOT, null, null, 0, null, false, null, 0L, null, null);
    }

    public static ShopMenuHolder subcategory(final String layoutGroupKey) {
        return new ShopMenuHolder(
            ViewType.SUBCATEGORY,
            Objects.requireNonNull(layoutGroupKey, "layoutGroupKey"),
            null,
            0,
            null,
            false,
            null,
            0L,
            null,
            null
        );
    }

    public static ShopMenuHolder browse(
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final boolean viaSubcategory
    ) {
        return new ShopMenuHolder(
            ViewType.BROWSE,
            Objects.requireNonNull(layoutGroupKey, "layoutGroupKey"),
            layoutChildKey,
            page,
            null,
            viaSubcategory,
            null,
            0L,
            null,
            null
        );
    }

    public static ShopMenuHolder detail(
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final ItemKey itemKey,
        final boolean viaSubcategory
    ) {
        return detail(layoutGroupKey, layoutChildKey, page, itemKey, viaSubcategory, null, 0L);
    }

    public static ShopMenuHolder detail(
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final ItemKey itemKey,
        final boolean viaSubcategory,
        final BigDecimal quotedUnitPrice
    ) {
        return detail(layoutGroupKey, layoutChildKey, page, itemKey, viaSubcategory, quotedUnitPrice, 0L);
    }

    public static ShopMenuHolder detail(
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final ItemKey itemKey,
        final boolean viaSubcategory,
        final BigDecimal quotedUnitPrice,
        final long quotedAtMillis
    ) {
        return new ShopMenuHolder(
            ViewType.DETAIL,
            layoutGroupKey,
            layoutChildKey,
            page,
            Objects.requireNonNull(itemKey, "itemKey"),
            viaSubcategory,
            quotedUnitPrice,
            quotedAtMillis,
            null,
            null
        );
    }

    public static ShopMenuHolder storeRoot() {
        return new ShopMenuHolder(ViewType.STORE_ROOT, null, null, 0, null, false, null, 0L, null, null);
    }

    public static ShopMenuHolder storeCategory(final String categoryId, final int page) {
        return new ShopMenuHolder(
            ViewType.STORE_CATEGORY,
            null,
            null,
            page,
            null,
            false,
            null,
            0L,
            Objects.requireNonNull(categoryId, "categoryId"),
            null
        );
    }

    public static ShopMenuHolder storeDetail(
        final String categoryId,
        final int page,
        final String productId
    ) {
        return new ShopMenuHolder(
            ViewType.STORE_DETAIL,
            null,
            null,
            page,
            null,
            false,
            null,
            0L,
            Objects.requireNonNull(categoryId, "categoryId"),
            Objects.requireNonNull(productId, "productId")
        );
    }

    public static ShopMenuHolder storeXpBottles() {
        return new ShopMenuHolder(
            ViewType.STORE_XP_BOTTLES,
            null,
            null,
            0,
            null,
            false,
            null,
            0L,
            "xp_bottles",
            null
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

    public String currentLayoutGroupKey() {
        return this.currentLayoutGroupKey;
    }

    public String currentLayoutChildKey() {
        return this.currentLayoutChildKey;
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

    public String currentStoreCategoryId() {
        return this.currentStoreCategoryId;
    }

    public String currentStoreProductId() {
        return this.currentStoreProductId;
    }
}
