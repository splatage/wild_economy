package com.splatage.wild_economy.gui;

import org.bukkit.entity.Player;

public final class ExchangeRootMenu {

    public void open(final Player player) {
        // Build and open root category menu
    }
}
package com.splatage.wild_economy.gui;

import org.bukkit.entity.Player;

public final class ExchangeRootMenu {

    public void open(final Player player) {
        // Build and open root category menu.
    }
}
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeRootMenu {

    private final ExchangeBrowseMenu exchangeBrowseMenu;

    public ExchangeRootMenu(final ExchangeBrowseMenu exchangeBrowseMenu) {
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
    }

    public void open(final Player player) {
        final Inventory inventory = Bukkit.createInventory(null, 27, "Shop");
        inventory.setItem(10, this.button(Material.WHEAT, "Farming"));
        inventory.setItem(11, this.button(Material.IRON_PICKAXE, "Mining"));
        inventory.setItem(12, this.button(Material.BONE, "Mob Drops"));
        inventory.setItem(14, this.button(Material.BRICKS, "Building"));
        inventory.setItem(15, this.button(Material.CHEST, "Utility"));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> this.exchangeBrowseMenu.open(player, ItemCategory.FARMING, 0);
            case 11 -> this.exchangeBrowseMenu.open(player, ItemCategory.MINING, 0);
            case 12 -> this.exchangeBrowseMenu.open(player, ItemCategory.MOB_DROPS, 0);
            case 14 -> this.exchangeBrowseMenu.open(player, ItemCategory.BUILDING, 0);
            case 15 -> this.exchangeBrowseMenu.open(player, ItemCategory.UTILITY, 0);
            case 22 -> player.closeInventory();
            default -> {
            }
        }
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
}
