package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.service.StoreService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class StoreRootMenu {

    private static final int[] CATEGORY_SLOTS = {10, 12, 14, 16, 22, 24};

    private final StoreService storeService;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private ShopMenuRouter shopMenuRouter;

    public StoreRootMenu(
        final StoreService storeService,
        final PlayerInfoItemFactory playerInfoItemFactory
    ) {
        this.storeService = Objects.requireNonNull(storeService, "storeService");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player) {
        final ShopMenuHolder holder = ShopMenuHolder.storeRoot();
        final Inventory inventory = holder.createInventory(27, "Shop - Store");

        final List<StoreCategory> categories = this.storeService.getCategories();
        for (int index = 0; index < categories.size() && index < CATEGORY_SLOTS.length; index++) {
            final StoreCategory category = categories.get(index);
            inventory.setItem(CATEGORY_SLOTS[index], this.button(this.resolveMaterial(category.iconKey()), category.displayName()));
        }

        inventory.setItem(4, this.playerInfoItemFactory.create(player));
        inventory.setItem(18, this.button(Material.ARROW, "Back"));
        inventory.setItem(26, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot == 18) {
            this.shopMenuRouter.openRoot(player);
            return;
        }
        if (rawSlot == 26) {
            player.closeInventory();
            return;
        }

        final List<StoreCategory> categories = this.storeService.getCategories();
        for (int index = 0; index < categories.size() && index < CATEGORY_SLOTS.length; index++) {
            if (CATEGORY_SLOTS[index] == rawSlot) {
                this.shopMenuRouter.openStoreCategory(player, categories.get(index).categoryId(), 0);
                return;
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

    private Material resolveMaterial(final String iconKey) {
        final Material material = Material.matchMaterial(iconKey);
        return material == null ? Material.BARRIER : material;
    }
}
