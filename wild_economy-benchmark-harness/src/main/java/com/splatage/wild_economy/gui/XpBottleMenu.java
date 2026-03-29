package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StorePurchaseResult;
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

public final class XpBottleMenu {

    private static final String XP_BOTTLES_CATEGORY_ID = "xp_bottles";

    private final StoreService storeService;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private ShopMenuRouter shopMenuRouter;

    public XpBottleMenu(
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
        final ShopMenuHolder holder = ShopMenuHolder.storeXpBottles();
        final Inventory inventory = holder.createInventory(27, "Store - XP Bottles");

        inventory.setItem(11, this.previewItem());
        inventory.setItem(13, this.purchaseItem(this.productByIndex(0)));
        inventory.setItem(14, this.purchaseItem(this.productByIndex(1)));
        inventory.setItem(15, this.purchaseItem(this.productByIndex(2)));
        inventory.setItem(21, this.playerInfoItemFactory.create(player));
        inventory.setItem(22, this.button(Material.BARRIER, "Back", List.of("Return to the Store")));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 13 -> this.purchase(player, this.productByIndex(0));
            case 14 -> this.purchase(player, this.productByIndex(1));
            case 15 -> this.purchase(player, this.productByIndex(2));
            case 22 -> this.shopMenuRouter.goBack(player);
            default -> {
            }
        }
    }

    private void purchase(final Player player, final StoreProduct product) {
        if (product == null) {
            player.sendMessage("This XP bottle option is not configured.");
            return;
        }

        final StorePurchaseResult result = this.storeService.purchase(player, product.productId());
        player.sendMessage(result.success() ? "Purchase successful." : result.message());

        this.open(player);
    }

    private StoreProduct productByIndex(final int index) {
        final List<StoreProduct> xpProducts = this.storeService.getProducts(XP_BOTTLES_CATEGORY_ID).stream()
                .filter(product -> product.type() == StoreProductType.XP_WITHDRAWAL)
                .sorted(Comparator.comparingInt(StoreProduct::xpCostPoints))
                .toList();

        if (index < 0 || index >= xpProducts.size()) {
            return null;
        }
        return xpProducts.get(index);
    }

    private ItemStack previewItem() {
        final ItemStack stack = new ItemStack(Material.EXPERIENCE_BOTTLE);
        final ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("XP Bottles");
            meta.setLore(List.of(
                    "Withdraw stored XP into bottles.",
                    "Throw a bottle to redeem its XP."
            ));
            stack.setItemMeta(meta);
        }

        return stack;
    }

    private ItemStack purchaseItem(final StoreProduct product) {
        if (product == null) {
            return this.button(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "Unavailable",
                    List.of("This XP bottle option is not configured.")
            );
        }

        return this.button(
                Material.GREEN_STAINED_GLASS_PANE,
                product.displayName(),
                List.of(
                        "XP Cost: " + product.xpCostPoints(),
                        "Click to purchase"
                )
        );
    }

    private ItemStack button(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
