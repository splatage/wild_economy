package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.gui.browse.ExchangeLayoutBrowseService;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public final class ShopMenuListener implements Listener {

    private final ExchangeLayoutBrowseService exchangeLayoutBrowseService;
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

    public ShopMenuListener(
        final ExchangeLayoutBrowseService exchangeLayoutBrowseService,
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
        this.exchangeLayoutBrowseService = Objects.requireNonNull(exchangeLayoutBrowseService, "exchangeLayoutBrowseService");
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

    @EventHandler
    public void onInventoryOpen(final InventoryOpenEvent event) {
        final ShopMenuHolder holder = ShopMenuRouter.getShopMenuHolder(event.getView().getTopInventory());
        if (holder == null || !this.isExchangeView(holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            this.exchangeLayoutBrowseService.handleExchangeViewOpened(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        final ShopMenuHolder holder = ShopMenuRouter.getShopMenuHolder(event.getView().getTopInventory());
        if (holder == null || !this.isExchangeView(holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            this.exchangeLayoutBrowseService.handleExchangeViewClosed(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (this.topSupplierMenu.isTopSupplierInventory(event.getView().getTopInventory())) {
            this.topSupplierMenu.handleClick(event);
            return;
        }
        if (this.marketActivityMenu.isMarketActivityInventory(event.getView().getTopInventory())) {
            this.marketActivityMenu.handleClick(event);
            return;
        }

        final ShopMenuHolder holder = ShopMenuRouter.getShopMenuHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        event.setCancelled(true);

        switch (holder.viewType()) {
            case ROOT -> this.exchangeRootMenu.handleClick(event);
            case SUBCATEGORY -> {
                if (holder.currentLayoutGroupKey() != null) {
                    this.exchangeSubcategoryMenu.handleClick(event, holder.currentLayoutGroupKey());
                }
            }
            case BROWSE -> {
                if (holder.currentLayoutGroupKey() != null) {
                    this.exchangeBrowseMenu.handleClick(
                        event,
                        holder.currentLayoutGroupKey(),
                        holder.currentLayoutChildKey(),
                        holder.currentPage(),
                        holder.viaSubcategory()
                    );
                }
            }
            case DETAIL -> this.exchangeItemDetailMenu.handleClick(event, holder);
            case STORE_ROOT -> this.storeRootMenu.handleClick(event);
            case STORE_CATEGORY -> {
                if (holder.currentStoreCategoryId() != null) {
                    this.storeCategoryMenu.handleClick(event, holder.currentStoreCategoryId(), holder.currentPage());
                }
            }
            case STORE_DETAIL -> this.storeProductDetailMenu.handleClick(event, holder);
            case STORE_XP_BOTTLES -> this.xpBottleMenu.handleClick(event);
        }
    }


    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (this.topSupplierMenu.isTopSupplierInventory(event.getView().getTopInventory())
            || this.marketActivityMenu.isMarketActivityInventory(event.getView().getTopInventory())) {
            final int topInventorySize = event.getView().getTopInventory().getSize();
            for (final int rawSlot : event.getRawSlots()) {
                if (rawSlot < topInventorySize) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        final ShopMenuHolder holder = ShopMenuRouter.getShopMenuHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        final int topInventorySize = event.getView().getTopInventory().getSize();
        for (final int rawSlot : event.getRawSlots()) {
            if (rawSlot < topInventorySize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isExchangeView(final ShopMenuHolder holder) {
        return switch (holder.viewType()) {
            case ROOT, SUBCATEGORY, BROWSE, DETAIL -> true;
            default -> false;
        };
    }
}
