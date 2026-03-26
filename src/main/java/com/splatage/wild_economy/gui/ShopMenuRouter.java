package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopMenuRouter {

    private final PlatformExecutor platformExecutor;
    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final StoreRootMenu storeRootMenu;
    private final StoreCategoryMenu storeCategoryMenu;
    private final StoreProductDetailMenu storeProductDetailMenu;

    public ShopMenuRouter(
        final PlatformExecutor platformExecutor,
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu,
        final StoreRootMenu storeRootMenu,
        final StoreCategoryMenu storeCategoryMenu,
        final StoreProductDetailMenu storeProductDetailMenu
    ) {
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
        this.storeRootMenu = Objects.requireNonNull(storeRootMenu, "storeRootMenu");
        this.storeCategoryMenu = Objects.requireNonNull(storeCategoryMenu, "storeCategoryMenu");
        this.storeProductDetailMenu = Objects.requireNonNull(storeProductDetailMenu, "storeProductDetailMenu");
    }

    public void openRoot(final Player player) {
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeRootMenu.open(player));
    }

    public void openSubcategory(final Player player, final ItemCategory category) {
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeSubcategoryMenu.open(player, category));
    }

    public void openBrowse(
        final Player player,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.exchangeBrowseMenu.open(player, category, generatedCategory, page, viaSubcategory)
        );
    }

    public void openDetail(final Player player, final ItemKey itemKey) {
        final ShopMenuHolder previous = this.currentHolder(player);
        final ItemCategory category = previous == null ? null : previous.currentCategory();
        final GeneratedItemCategory generatedCategory =
            previous == null ? null : previous.currentGeneratedCategory();
        final int page = previous == null ? 0 : previous.currentPage();
        final boolean viaSubcategory = previous != null && previous.viaSubcategory();

        this.platformExecutor.runOnPlayer(
            player,
            () -> this.exchangeItemDetailMenu.open(
                player,
                itemKey,
                1,
                category,
                generatedCategory,
                page,
                viaSubcategory
            )
        );
    }

    public void openStoreRoot(final Player player) {
        this.platformExecutor.runOnPlayer(player, () -> this.storeRootMenu.open(player));
    }

    public void openStoreCategory(final Player player, final String categoryId, final int page) {
        this.platformExecutor.runOnPlayer(player, () -> this.storeCategoryMenu.open(player, categoryId, page));
    }

    public void openStoreDetail(final Player player, final String categoryId, final int page, final String productId) {
        this.platformExecutor.runOnPlayer(player, () -> this.storeProductDetailMenu.open(player, categoryId, page, productId));
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
                if (holder.viaSubcategory() && holder.currentCategory() != null) {
                    this.openSubcategory(player, holder.currentCategory());
                } else {
                    this.openRoot(player);
                }
            }
            case DETAIL -> {
                if (holder.currentCategory() == null) {
                    this.openRoot(player);
                } else {
                    this.openBrowse(
                        player,
                        holder.currentCategory(),
                        holder.currentGeneratedCategory(),
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
        }
    }

    public void closeAllShopViews() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (this.currentHolder(player) == null) {
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
