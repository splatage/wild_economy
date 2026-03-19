package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.service.ExchangeCatalogView;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeBrowseMenu {

    private final ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeBrowseMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player, final ItemCategory category, final int page) {
        final Inventory inventory = Bukkit.createInventory(null, 54, "Shop - " + this.prettyCategory(category));
        final List<ExchangeCatalogView> entries = this.exchangeService.browseCategory(category, page, 45);

        int slot = 0;
        for (final ExchangeCatalogView view : entries) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.catalogItem(view));
            slot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.button(Material.ARROW, "Next"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final ItemCategory category, final int page) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta() || !clicked.getItemMeta().hasLocalizedName()) {
                return;
            }
            final String itemKeyValue = clicked.getItemMeta().getLocalizedName();
            this.shopMenuRouter.openDetail(player, new ItemKey(itemKeyValue));
            return;
        }

        switch (slot) {
            case 45 -> this.shopMenuRouter.openRoot(player);
            case 49 -> player.closeInventory();
            case 53 -> this.shopMenuRouter.openBrowse(player, category, page + 1);
            default -> {
            }
        }
    }

    private ItemStack catalogItem(final ExchangeCatalogView view) {
        final Material material = this.resolveMaterial(view.itemKey());
        final ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(view.displayName());
            meta.setLocalizedName(view.itemKey().value());
            meta.setLore(List.of(
                "Price: " + view.buyPrice(),
                "Stock: " + view.stockCount(),
                "State: " + view.stockState().name()
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack button(final Material material, final String name) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material resolveMaterial(final ItemKey itemKey) {
        return Material.matchMaterial(itemKey.value().replace("minecraft:", "").toUpperCase());
    }

    private String prettyCategory(final ItemCategory category) {
        return switch (category) {
            case FARMING -> "Farming";
            case MINING -> "Mining";
            case MOB_DROPS -> "Mob Drops";
            case BUILDING -> "Building";
            case UTILITY -> "Utility";
        };
    }
}
