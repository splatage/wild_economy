package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.activity.MarketActivityCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopMenuRouter {

    private static final String XP_BOTTLES_CATEGORY_ID = "xp_bottles";

    private final PlatformExecutor platformExecutor;
    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final StoreRootMenu storeRootMenu;
    private final StoreCategoryMenu storeCategoryMenu;
    private final StoreProductDetailMenu storeProductDetailMenu;
    private final XpBottleMenu xpBottleMenu;
    private final TopSupplierMenu topSupplierMenu;
    private final MarketActivityMenu marketActivityMenu;

    public ShopMenuRouter(
        final PlatformExecutor platformExecutor,
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu,
        final StoreRootMenu storeRootMenu,
        final StoreCategoryMenu storeCategoryMenu,
        final StoreProductDetailMenu storeProductDetailMenu,
        final XpBottleMenu xpBottleMenu,
        final TopSupplierMenu topSupplierMenu,
        final MarketActivityMenu marketActivityMenu
    ) {
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
        this.storeRootMenu = Objects.requireNonNull(storeRootMenu, "storeRootMenu");
        this.storeCategoryMenu = Objects.requireNonNull(storeCategoryMenu, "storeCategoryMenu");
        this.storeProductDetailMenu = Objects.requireNonNull(storeProductDetailMenu, "storeProductDetailMenu");
        this.xpBottleMenu = Objects.requireNonNull(xpBottleMenu, "xpBottleMenu");
        this.topSupplierMenu = Objects.requireNonNull(topSupplierMenu, "topSupplierMenu");
        this.marketActivityMenu = Objects.requireNonNull(marketActivityMenu, "marketActivityMenu");
    }

    public void openRoot(final Player player) {
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeRootMenu.open(player));
    }

    public void openSubcategory(final Player player, final String layoutGroupKey) {
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeSubcategoryMenu.open(player, layoutGroupKey));
    }

    public void openBrowse(
        final Player player,
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final boolean viaSubcategory
    ) {
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.exchangeBrowseMenu.open(player, layoutGroupKey, layoutChildKey, page, viaSubcategory)
        );
    }

    public void openDetail(final Player player, final ItemKey itemKey) {
        final ShopMenuHolder previous = this.currentHolder(player);
        final String layoutGroupKey = previous == null ? null : previous.currentLayoutGroupKey();
        final String layoutChildKey = previous == null ? null : previous.currentLayoutChildKey();
        final int page = previous == null ? 0 : previous.currentPage();
        final boolean viaSubcategory = previous != null && previous.viaSubcategory();

        this.platformExecutor.runOnPlayer(
            player,
            () -> this.exchangeItemDetailMenu.open(
                player,
                itemKey,
                1,
                layoutGroupKey,
                layoutChildKey,
                page,
                viaSubcategory
            )
        );
    }

    public void openStoreRoot(final Player player) {
        this.platformExecutor.runOnPlayer(player, () -> this.storeRootMenu.open(player));
    }

    public void openStoreCategory(final Player player, final String categoryId, final int page) {
        if (XP_BOTTLES_CATEGORY_ID.equals(categoryId)) {
            this.openStoreXpBottles(player);
            return;
        }
        this.platformExecutor.runOnPlayer(player, () -> this.storeCategoryMenu.open(player, categoryId, page));
    }

    public void openStoreDetail(final Player player, final String categoryId, final int page, final String productId) {
        this.platformExecutor.runOnPlayer(player, () -> this.storeProductDetailMenu.open(player, categoryId, page, productId));
    }

    public void openStoreXpBottles(final Player player) {
        this.platformExecutor.runOnPlayer(player, () -> this.xpBottleMenu.open(player));
    }

    public void openMarketActivityRoot(final Player player) {
        this.platformExecutor.runOnPlayer(player, () -> this.marketActivityMenu.openRoot(player));
    }

    public void openMarketActivityCategory(final Player player, final MarketActivityCategory category) {
        this.platformExecutor.runOnPlayer(player, () -> this.marketActivityMenu.openCategory(player, category));
    }
    public void openTopSuppliers(final Player player) {
        this.platformExecutor.runOnPlayer(player, () -> this.topSupplierMenu.open(player));
    }

    public void openTopSuppliers(final Player player, final com.splatage.wild_economy.exchange.supplier.SupplierScope scope) {
        this.platformExecutor.runOnPlayer(player, () -> this.topSupplierMenu.open(player, scope));
    }

    public void openTopSupplierDetail(
        final Player player,
        final java.util.UUID selectedPlayerId,
        final com.splatage.wild_economy.exchange.supplier.SupplierScope scope
    ) {
        this.platformExecutor.runOnPlayer(player, () -> this.topSupplierMenu.openPlayerDetail(player, selectedPlayerId, scope));
    }


    public void goBack(final Player player) {
        final ShopMenuHolder holder = this.currentHolder(player);
        if (holder == null) {
            this.openRoot(player);
            return;
        }

        switch (holder.viewType()) {
            case ROOT -> this.openRoot(player);
            case SUBCATEGORY -> this.openRoot(player);
            case BROWSE -> {
                if (holder.viaSubcategory() && holder.currentLayoutGroupKey() != null) {
                    this.openSubcategory(player, holder.currentLayoutGroupKey());
                } else {
                    this.openRoot(player);
                }
            }
            case DETAIL -> {
                if (holder.currentLayoutGroupKey() == null) {
                    this.openRoot(player);
                } else {
                    this.openBrowse(
                        player,
                        holder.currentLayoutGroupKey(),
                        holder.currentLayoutChildKey(),
                        holder.currentPage(),
                        holder.viaSubcategory()
                    );
                }
            }
            case STORE_ROOT -> this.openRoot(player);
            case STORE_CATEGORY -> this.openStoreRoot(player);
            case STORE_DETAIL -> {
                if (holder.currentStoreCategoryId() == null) {
                    this.openStoreRoot(player);
                } else {
                    this.openStoreCategory(player, holder.currentStoreCategoryId(), holder.currentPage());
                }
            }
            case STORE_XP_BOTTLES -> this.openStoreRoot(player);
        }
    }

    public void closeAllShopViews() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (this.currentHolder(player) == null
                && !this.topSupplierMenu.isTopSupplierInventory(topInventory)
                && !this.marketActivityMenu.isMarketActivityInventory(topInventory)) {
                continue;
            }
            this.platformExecutor.runOnPlayer(player, player::closeInventory);
        }
    }

    private ShopMenuHolder currentHolder(final Player player) {
        return getShopMenuHolder(player.getOpenInventory().getTopInventory());
    }

    public static ShopMenuHolder getShopMenuHolder(final Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        final InventoryHolder holder = inventory.getHolder();
        if (holder instanceof ShopMenuHolder shopMenuHolder) {
            return shopMenuHolder;
        }

        return null;
    }
}
