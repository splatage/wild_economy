package com.splatage.wild_economy.gui;

import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ShopMenuListener implements Listener {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final StoreRootMenu storeRootMenu;
    private final StoreCategoryMenu storeCategoryMenu;
    private final StoreProductDetailMenu storeProductDetailMenu;
    private final XpBottleMenu xpBottleMenu;

    public ShopMenuListener(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu,
        final StoreRootMenu storeRootMenu,
        final StoreCategoryMenu storeCategoryMenu,
        final StoreProductDetailMenu storeProductDetailMenu,
        final XpBottleMenu xpBottleMenu
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
        this.storeRootMenu = Objects.requireNonNull(storeRootMenu, "storeRootMenu");
        this.storeCategoryMenu = Objects.requireNonNull(storeCategoryMenu, "storeCategoryMenu");
        this.storeProductDetailMenu = Objects.requireNonNull(storeProductDetailMenu, "storeProductDetailMenu");
        this.xpBottleMenu = Objects.requireNonNull(xpBottleMenu, "xpBottleMenu");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
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
}
