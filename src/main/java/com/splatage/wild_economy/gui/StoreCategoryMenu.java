package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.service.StoreService;
import com.splatage.wild_economy.store.state.StoreOwnershipState;
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
    private static final int TILE_LORE_LINE_LIMIT = 2;

    private final StoreService storeService;
    private final EconomyConfig economyConfig;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private final StorePresentationFormatter presentationFormatter;
    private ShopMenuRouter shopMenuRouter;

    public StoreCategoryMenu(
        final StoreService storeService,
        final EconomyConfig economyConfig,
        final PlayerInfoItemFactory playerInfoItemFactory
    ) {
        this.storeService = Objects.requireNonNull(storeService, "storeService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
        this.presentationFormatter = new StorePresentationFormatter();
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player, final String categoryId, final int page) {
        this.storeService.ensurePlayerLoadedAsync(player.getUniqueId());

        final StoreCategory category = this.resolveCategory(player, categoryId);
        final ShopMenuHolder holder = ShopMenuHolder.storeCategory(categoryId, page);
        final Inventory inventory = holder.createInventory(54, "Store - " + category.displayName());

        final List<StoreProduct> products = this.storeService.getVisibleProducts(player, categoryId);
        final int start = Math.max(0, page) * PAGE_SIZE;

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            final int index = start + slot;
            if (index >= products.size()) {
                break;
            }
            inventory.setItem(slot, this.productItem(player, products.get(index)));
        }

        if (page > 0) {
            inventory.setItem(45, this.button(Material.ARROW, this.presentationFormatter.previousButtonName()));
        }
        inventory.setItem(48, this.playerInfoItemFactory.create(player));
        inventory.setItem(49, this.button(Material.BARRIER, this.presentationFormatter.backButtonName()));
        if ((page + 1) * PAGE_SIZE < products.size()) {
            inventory.setItem(53, this.button(Material.ARROW, this.presentationFormatter.nextButtonName()));
        }

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final String categoryId, final int page) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < PAGE_SIZE) {
            final List<StoreProduct> products = this.storeService.getVisibleProducts(player, categoryId);
            final int index = Math.max(0, page) * PAGE_SIZE + rawSlot;
            if (index >= 0 && index < products.size()) {
                this.shopMenuRouter.openStoreDetail(player, categoryId, page, products.get(index).productId());
            }
            return;
        }

        switch (rawSlot) {
            case 45 -> {
                if (page > 0) {
                    this.shopMenuRouter.openStoreCategory(player, categoryId, page - 1);
                }
            }
            case 49 -> this.shopMenuRouter.goBack(player);
            case 53 -> {
                final List<StoreProduct> products = this.storeService.getVisibleProducts(player, categoryId);
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
            final StoreEligibilityResult eligibility = this.storeService.getProductEligibility(player, product.productId());
            final StoreOwnershipState ownershipState = product.type() == StoreProductType.XP_WITHDRAWAL
                    ? StoreOwnershipState.NOT_OWNED
                    : this.storeService.getOwnershipState(player.getUniqueId(), product.entitlementKey());

            meta.setDisplayName(this.presentationFormatter.productDisplayName(product));
            meta.setLore(this.presentationFormatter.tileLore(
                    product,
                    eligibility,
                    ownershipState,
                    this.economyConfig,
                    TILE_LORE_LINE_LIMIT
            ));
            this.presentationFormatter.applyMenuFlags(meta);
            stack.setItemMeta(meta);
        }

        return stack;
    }

    private ItemStack button(final Material material, final String name) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            this.presentationFormatter.applyMenuFlags(meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private StoreCategory resolveCategory(final Player player, final String categoryId) {
        return this.storeService.getVisibleCategories(player).stream()
                .filter(category -> category.categoryId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown store category: " + categoryId));
    }

    private Material resolveMaterial(final String iconKey) {
        final Material material = Material.matchMaterial(iconKey);
        return material == null ? Material.BARRIER : material;
    }
}
