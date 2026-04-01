package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
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
    private final StorePresentationFormatter presentationFormatter;
    private ShopMenuRouter shopMenuRouter;

    public StoreRootMenu(
        final StoreService storeService,
        final PlayerInfoItemFactory playerInfoItemFactory
    ) {
        this.storeService = Objects.requireNonNull(storeService, "storeService");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
        this.presentationFormatter = new StorePresentationFormatter();
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player) {
        final ShopMenuHolder holder = ShopMenuHolder.storeRoot();
        final Inventory inventory = holder.createInventory(INVENTORY_SIZE, "Shop - Store");

        for (final StoreCategory category : this.sortedCategories(player)) {
            this.validateSlot(category);
            final StoreEligibilityResult eligibility = this.storeService.getCategoryEligibility(player, category.categoryId());
            inventory.setItem(
                    category.slot(),
                    this.button(this.resolveMaterial(category.iconKey()), this.presentationFormatter.categoryDisplayName(category), this.presentationFormatter.rootCategoryLore(category, eligibility))
            );
        }

        inventory.setItem(21, this.playerInfoItemFactory.create(player));
        inventory.setItem(26, this.button(Material.BARRIER, this.presentationFormatter.closeButtonName(), null));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot == 26) {
            this.shopMenuRouter.goBack(player);
            return;
        }

        for (final StoreCategory category : this.sortedCategories(player)) {
            this.validateSlot(category);
            if (category.slot() == rawSlot) {
                this.shopMenuRouter.openStoreCategory(player, category.categoryId(), 0);
                return;
            }
        }
    }

    private List<StoreCategory> sortedCategories(final Player player) {
        return this.storeService.getVisibleCategories(player).stream()
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

    private ItemStack button(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            this.presentationFormatter.applyMenuFlags(meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material resolveMaterial(final String iconKey) {
        final Material material = Material.matchMaterial(iconKey);
        return material == null ? Material.BARRIER : material;
    }
}
