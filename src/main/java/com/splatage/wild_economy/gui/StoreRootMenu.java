package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.service.StoreService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class StoreRootMenu {

    private static final int INVENTORY_SIZE = 27;

    private final StoreService storeService;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private final MenuHeadFactory menuHeadFactory;
    private ShopMenuRouter shopMenuRouter;

    public StoreRootMenu(
        final StoreService storeService,
        final PlayerInfoItemFactory playerInfoItemFactory,
        final MenuHeadFactory menuHeadFactory
    ) {
        this.storeService = Objects.requireNonNull(storeService, "storeService");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
        this.menuHeadFactory = Objects.requireNonNull(menuHeadFactory, "menuHeadFactory");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player) {
        final ShopMenuHolder holder = ShopMenuHolder.storeRoot();
        final Inventory inventory = holder.createInventory(INVENTORY_SIZE, "Shop - Store");

        for (final StoreCategory category : this.sortedCategories()) {
            this.validateSlot(category);
            inventory.setItem(
                    category.slot(),
                    this.button(this.resolveMaterial(category.iconKey()), category.displayName())
            );
        }

        this.menuHeadFactory.placeWord(inventory, 2, "STORE");
        inventory.setItem(18, this.button(Material.ARROW, "Back"));
        inventory.setItem(21, this.playerInfoItemFactory.create(player));
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

        for (final StoreCategory category : this.sortedCategories()) {
            this.validateSlot(category);
            if (category.slot() == rawSlot) {
                this.shopMenuRouter.openStoreCategory(player, category.categoryId(), 0);
                return;
            }
        }
    }

    private List<StoreCategory> sortedCategories() {
        return this.storeService.getCategories().stream()
                .sorted(Comparator.comparingInt(StoreCategory::slot))
                .toList();
    }

    private void validateSlot(final StoreCategory category) {
        if (category.slot() < 0 || category.slot() >= INVENTORY_SIZE) {
            throw new IllegalStateException(
                    "Store category '" + category.categoryId()
                            + "' has slot " + category.slot()
                            + " which is out of range for a 27-slot Store root menu"
            );
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
