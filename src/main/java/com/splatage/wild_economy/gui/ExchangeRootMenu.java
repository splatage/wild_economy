package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeRootMenu {

    private final ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeRootMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player) {
        final ShopMenuHolder holder = ShopMenuHolder.root();
        final Inventory inventory = holder.createInventory(27, "Shop");

        inventory.setItem(10, this.button(Material.BREAD, ItemCategory.FARMING_AND_FOOD.displayName()));
        inventory.setItem(11, this.button(Material.IRON_PICKAXE, ItemCategory.MINING_AND_MINERALS.displayName()));
        inventory.setItem(12, this.button(Material.BONE, ItemCategory.MOB_DROPS.displayName()));
        inventory.setItem(13, this.button(Material.BRICKS, ItemCategory.BUILDING_MATERIALS.displayName()));
        inventory.setItem(14, this.button(Material.REDSTONE, ItemCategory.REDSTONE_AND_UTILITY.displayName()));
        inventory.setItem(15, this.button(Material.DIAMOND_SWORD, ItemCategory.COMBAT_AND_ADVENTURE.displayName()));
        inventory.setItem(16, this.button(Material.CHEST, ItemCategory.MISC.displayName()));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> this.openCategory(player, ItemCategory.FARMING_AND_FOOD);
            case 11 -> this.openCategory(player, ItemCategory.MINING_AND_MINERALS);
            case 12 -> this.openCategory(player, ItemCategory.MOB_DROPS);
            case 13 -> this.openCategory(player, ItemCategory.BUILDING_MATERIALS);
            case 14 -> this.openCategory(player, ItemCategory.REDSTONE_AND_UTILITY);
            case 15 -> this.openCategory(player, ItemCategory.COMBAT_AND_ADVENTURE);
            case 16 -> this.openCategory(player, ItemCategory.MISC);
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void openCategory(final Player player, final ItemCategory category) {
        final int visibleCount = this.exchangeService.countVisibleItems(category, null);
        final List<GeneratedItemCategory> subcategories = this.exchangeService.listVisibleSubcategories(category);

        if (visibleCount > 45 && subcategories.size() > 1) {
            this.shopMenuRouter.openSubcategory(player, category);
            return;
        }

        this.shopMenuRouter.openBrowse(player, category, null, 0, false);
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

