package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.service.ExchangeCatalogView;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeBrowseMenu {

    private final ExchangeService exchangeService;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private final LayoutBlueprint layoutBlueprint;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeBrowseMenu(
        final ExchangeService exchangeService,
        final PlayerInfoItemFactory playerInfoItemFactory,
        final LayoutBlueprint layoutBlueprint
    ) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
        this.layoutBlueprint = Objects.requireNonNull(layoutBlueprint, "layoutBlueprint");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(
        final Player player,
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final boolean viaSubcategory
    ) {
        final ShopMenuHolder holder = ShopMenuHolder.browse(layoutGroupKey, layoutChildKey, page, viaSubcategory);
        final Inventory inventory = holder.createInventory(54, this.title(layoutGroupKey, layoutChildKey));
        final List<ExchangeCatalogView> entries = this.exchangeService.browseLayout(layoutGroupKey, layoutChildKey, page, 45);

        int slot = 0;
        for (final ExchangeCatalogView view : entries) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.catalogItem(view));
            slot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(48, this.playerInfoItemFactory.create(player));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.button(Material.ARROW, "Next"));

        player.openInventory(inventory);
    }

    public void handleClick(
        final InventoryClickEvent event,
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final boolean viaSubcategory
    ) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return;
            }

            final ItemKey itemKey = this.toItemKey(clicked.getType());
            this.shopMenuRouter.openDetail(player, itemKey);
            return;
        }

        switch (slot) {
            case 45 -> {
                if (viaSubcategory) {
                    this.shopMenuRouter.openSubcategory(player, layoutGroupKey);
                } else {
                    this.shopMenuRouter.openRoot(player);
                }
            }
            case 49 -> player.closeInventory();
            case 53 -> this.shopMenuRouter.openBrowse(player, layoutGroupKey, layoutChildKey, page + 1, viaSubcategory);
            default -> {
            }
        }
    }

    private String title(final String layoutGroupKey, final String layoutChildKey) {
        final String groupLabel = this.layoutBlueprint.group(layoutGroupKey)
            .map(group -> group.label())
            .orElse(layoutGroupKey == null ? "Shop" : layoutGroupKey);
        if (layoutChildKey == null || layoutChildKey.isBlank()) {
            return "Shop - " + groupLabel;
        }
        final String childLabel = this.layoutBlueprint.group(layoutGroupKey)
            .flatMap(group -> group.child(layoutChildKey))
            .map(child -> child.label())
            .orElse(layoutChildKey);
        return "Shop - " + groupLabel + " / " + childLabel;
    }

    private ItemStack catalogItem(final ExchangeCatalogView view) {
        final Material material = this.resolveMaterial(view.itemKey());
        final ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        final ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(view.displayName());
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

    private ItemKey toItemKey(final Material material) {
        return new ItemKey("minecraft:" + material.name().toLowerCase());
    }
}
