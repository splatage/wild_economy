package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class ShopMenuListener implements Listener {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;

    public ShopMenuListener(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null) {
            return;
        }

        if (title.equals("Shop")) {
            this.exchangeRootMenu.handleClick(event);
            return;
        }

        if (title.startsWith("Shop - ")) {
            final ItemCategory category = this.parseCategory(title.substring("Shop - ".length()));
            if (category != null) {
                this.exchangeBrowseMenu.handleClick(event, category, 0);
            }
            return;
        }

        if (title.startsWith("Buy - ")) {
            final var current = event.getInventory().getItem(11);
            if (current != null && current.hasItemMeta() && current.getItemMeta().hasLocalizedName()) {
                this.exchangeItemDetailMenu.handleClick(event, new ItemKey(current.getItemMeta().getLocalizedName()));
            }
        }
    }

    private ItemCategory parseCategory(final String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "farming" -> ItemCategory.FARMING;
            case "mining" -> ItemCategory.MINING;
            case "mob drops" -> ItemCategory.MOB_DROPS;
            case "building" -> ItemCategory.BUILDING;
            case "utility" -> ItemCategory.UTILITY;
            default -> null;
        };
    }
}
