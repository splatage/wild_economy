package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.service.StoreService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class StoreCategoryMenu {

    private static final int PAGE_SIZE = 45;

    private final StoreService storeService;
    private ShopMenuRouter shopMenuRouter;

    public StoreCategoryMenu(final StoreService storeService) {
        this.storeService = Objects.requireNonNull(storeService, "storeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player, final String categoryId, final int page) {
        final StoreCategory category = this.resolveCategory(categoryId);
        final ShopMenuHolder holder = ShopMenuHolder.storeCategory(categoryId, page);
        final Inventory inventory = holder.createInventory(54, "Store - " + category.displayName());

        final List<StoreProduct> products = this.storeService.getProducts(categoryId);
        final int start = Math.max(0, page) * PAGE_SIZE;

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            final int index = start + slot;
            if (index >= products.size()) {
                break;
            }
            inventory.setItem(slot, this.productItem(player, products.get(index)));
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.button(Material.ARROW, "Next"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final String categoryId, final int page) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < PAGE_SIZE) {
            final List<StoreProduct> products = this.storeService.getProducts(categoryId);
            final int index = Math.max(0, page) * PAGE_SIZE + rawSlot;
            if (index >= 0 && index < products.size()) {
                this.shopMenuRouter.openStoreDetail(player, categoryId, page, products.get(index).productId());
            }
            return;
        }

        switch (rawSlot) {
            case 45 -> this.shopMenuRouter.openStoreRoot(player);
            case 49 -> player.closeInventory();
            case 53 -> {
                final List<StoreProduct> products = this.storeService.getProducts(categoryId);
                final int nextStart = (page + 1) * PAGE_SIZE;
                if (nextStart < products.size()) {
                    this.shopMenuRouter.openStoreCategory(player, categoryId, page + 1);
                }
            }
            default -> {
            }
        }
    }

    private ItemStack productItem(final Player player, final StoreProduct product) {
        final Material material = this.resolveMaterial(product.iconKey());
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(product.displayName());

            final List<String> lore = new ArrayList<>();
            if (product.type() == StoreProductType.XP_WITHDRAWAL) {
                lore.add("XP Cost: " + product.xpCostPoints());
                lore.add("Type: XP_WITHDRAWAL");
                lore.add("Throw to redeem");
            } else {
                final boolean owned = product.entitlementKey() != null
                        && !product.entitlementKey().isBlank()
                        && this.storeService.ownsEntitlement(player.getUniqueId(), product.entitlementKey());

                lore.add("Price: " + product.price().minorUnits());
                lore.add("Type: " + product.type().name());
                lore.add(owned ? "Owned: Yes" : "Owned: No");
            }
            lore.add("Click to view");

            meta.setLore(lore);
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

    private StoreCategory resolveCategory(final String categoryId) {
        return this.storeService.getCategories().stream()
                .filter(category -> category.categoryId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown store category: " + categoryId));
    }

    private Material resolveMaterial(final String iconKey) {
        final Material material = Material.matchMaterial(iconKey);
        return material == null ? Material.BARRIER : material;
    }
}
