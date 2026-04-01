package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StorePurchaseResult;
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

public final class StoreProductDetailMenu {

    private final StoreService storeService;
    private final EconomyConfig economyConfig;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private final StorePresentationFormatter presentationFormatter;
    private ShopMenuRouter shopMenuRouter;

    public StoreProductDetailMenu(
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

    public void open(final Player player, final String categoryId, final int page, final String productId) {
        this.storeService.ensurePlayerLoadedAsync(player.getUniqueId());

        final StoreProduct product = this.resolveProduct(productId);
        final StoreEligibilityResult eligibility = this.storeService.getProductEligibility(player, productId);
        if (!eligibility.visible()) {
            this.shopMenuRouter.goBack(player);
            return;
        }

        final ShopMenuHolder holder = ShopMenuHolder.storeDetail(categoryId, page, productId);
        final Inventory inventory = holder.createInventory(27, "Store - " + product.displayName());

        inventory.setItem(21, this.playerInfoItemFactory.create(player));
        inventory.setItem(11, this.detailItem(player, product, eligibility));
        inventory.setItem(13, this.purchaseButton(player, product, eligibility));
        inventory.setItem(22, this.button(Material.BARRIER, this.presentationFormatter.backButtonName(), this.presentationFormatter.backButtonLore()));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final ShopMenuHolder holder) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final String categoryId = holder.currentStoreCategoryId();
        final String productId = holder.currentStoreProductId();
        if (categoryId == null || productId == null) {
            return;
        }

        switch (event.getRawSlot()) {
            case 13 -> {
                final StoreEligibilityResult eligibility = this.storeService.getProductEligibility(player, productId);
                if (!eligibility.acquirable()) {
                    player.sendMessage(eligibility.blockedMessage() == null ? "This product is locked." : eligibility.blockedMessage());
                    this.open(player, categoryId, holder.currentPage(), productId);
                    return;
                }
                final StorePurchaseResult result = this.storeService.purchase(player, productId);
                player.sendMessage(result.success() ? "Purchase successful." : result.message());

                if (result.product() != null) {
                    this.open(player, categoryId, holder.currentPage(), productId);
                }
            }
            case 22 -> this.shopMenuRouter.goBack(player);
            default -> {
            }
        }
    }

    private ItemStack detailItem(final Player player, final StoreProduct product, final StoreEligibilityResult eligibility) {
        final Material material = this.resolveMaterial(product.iconKey());
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            final StoreOwnershipState ownershipState = product.type() == StoreProductType.XP_WITHDRAWAL
                    ? StoreOwnershipState.NOT_OWNED
                    : this.storeService.getOwnershipState(player.getUniqueId(), product.entitlementKey());
            meta.setDisplayName(this.presentationFormatter.productDisplayName(product));
            meta.setLore(this.presentationFormatter.detailLore(product, eligibility, ownershipState, this.economyConfig));
            this.presentationFormatter.applyMenuFlags(meta);
            stack.setItemMeta(meta);
        }

        return stack;
    }

    private ItemStack purchaseButton(final Player player, final StoreProduct product, final StoreEligibilityResult eligibility) {
        final StoreOwnershipState ownershipState = product.type() == StoreProductType.XP_WITHDRAWAL
                ? StoreOwnershipState.NOT_OWNED
                : this.storeService.getOwnershipState(player.getUniqueId(), product.entitlementKey());
        final StorePresentationFormatter.PurchaseButtonView view = this.presentationFormatter.purchaseButton(
                product,
                eligibility,
                ownershipState,
                this.economyConfig
        );
        return this.button(view.material(), view.name(), view.lore());
    }

    private ItemStack button(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            this.presentationFormatter.applyMenuFlags(meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private StoreProduct resolveProduct(final String productId) {
        return this.storeService.getCategories().stream()
                .flatMap(category -> this.storeService.getProducts(category.categoryId()).stream())
                .filter(product -> product.productId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown store product: " + productId));
    }

    private Material resolveMaterial(final String iconKey) {
        final Material material = Material.matchMaterial(iconKey);
        return material == null ? Material.BARRIER : material;
    }
}
