package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ShopMenuListener implements Listener {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final ShopMenuRouter shopMenuRouter;

    public ShopMenuListener(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu,
        final ShopMenuRouter shopMenuRouter
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null) {
            return;
        }
        if (!(title.equals("Shop") || title.startsWith("Shop - ") || title.startsWith("Buy - "))) {
            return;
        }

        // Always cancel clicks in shop-managed inventories first.
        event.setCancelled(true);

        final MenuSession session = this.shopMenuRouter.getSession(event.getWhoClicked().getUniqueId());
        if (session == null) {
            if (title.equals("Shop")) {
                this.exchangeRootMenu.handleClick(event);
            }
            return;
        }

        switch (session.viewType()) {
            case ROOT -> this.exchangeRootMenu.handleClick(event);
            case SUBCATEGORY -> {
                if (session.currentCategory() != null) {
                    this.exchangeSubcategoryMenu.handleClick(event, session.currentCategory());
                }
            }
            case BROWSE -> {
                if (session.currentCategory() != null) {
                    this.exchangeBrowseMenu.handleClick(
                        event,
                        session.currentCategory(),
                        session.currentGeneratedCategory(),
                        session.currentPage(),
                        session.viaSubcategory()
                    );
                }
            }
            case DETAIL -> {
                if (session.currentItemKey() != null) {
                    this.exchangeItemDetailMenu.handleClick(event, session.currentItemKey());
                    return;
                }
                final var current = event.getInventory().getItem(11);
                if (current != null && current.getType() != Material.AIR) {
                    this.exchangeItemDetailMenu.handleClick(event, this.toItemKey(current.getType()));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.shopMenuRouter.clearSession(event.getPlayer().getUniqueId());
    }

    private ItemKey toItemKey(final Material material) {
        return new ItemKey("minecraft:" + material.name().toLowerCase());
    }
}
