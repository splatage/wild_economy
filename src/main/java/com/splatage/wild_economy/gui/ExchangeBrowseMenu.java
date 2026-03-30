package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.item.ExchangeItemCodec;
import com.splatage.wild_economy.exchange.service.ExchangeCatalogView;
import com.splatage.wild_economy.gui.browse.ExchangeLayoutBrowseService;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeBrowseMenu {

    private final ExchangeLayoutBrowseService exchangeLayoutBrowseService;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private final LayoutBlueprint layoutBlueprint;
    private final ExchangeItemCodec exchangeItemCodec;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeBrowseMenu(
        final ExchangeLayoutBrowseService exchangeLayoutBrowseService,
        final PlayerInfoItemFactory playerInfoItemFactory,
        final LayoutBlueprint layoutBlueprint
    ) {
        this.exchangeLayoutBrowseService = Objects.requireNonNull(exchangeLayoutBrowseService, "exchangeLayoutBrowseService");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
        this.layoutBlueprint = Objects.requireNonNull(layoutBlueprint, "layoutBlueprint");
        this.exchangeItemCodec = new ExchangeItemCodec();
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
        final List<ExchangeCatalogView> entries = this.exchangeLayoutBrowseService.browseLayout(layoutGroupKey, layoutChildKey, page, 45);
        final Map<Integer, ItemKey> slotItemKeys = new HashMap<>();
        for (int slot = 0; slot < entries.size() && slot < 45; slot++) {
            slotItemKeys.put(slot, entries.get(slot).itemKey());
        }
        final ShopMenuHolder holder = ShopMenuHolder.browse(layoutGroupKey, layoutChildKey, page, viaSubcategory, slotItemKeys);
        final Inventory inventory = holder.createInventory(54, this.title(layoutGroupKey, layoutChildKey));
        final int totalVisible = this.exchangeLayoutBrowseService.countVisibleItems(layoutGroupKey, layoutChildKey);

        int slot = 0;
        for (final ExchangeCatalogView view : entries) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.catalogItem(view));
            slot++;
        }

        if (page > 0) {
            inventory.setItem(45, this.button(Material.ARROW, "Previous"));
        }
        inventory.setItem(48, this.playerInfoItemFactory.create(player));
        inventory.setItem(49, this.button(Material.BARRIER, "Back"));
        if ((page + 1) * 45 < totalVisible) {
            inventory.setItem(53, this.button(Material.ARROW, "Next"));
        }

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

        final ShopMenuHolder holder = event.getView().getTopInventory().getHolder() instanceof ShopMenuHolder shopMenuHolder
            ? shopMenuHolder
            : null;

        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            if (holder == null) {
                return;
            }
            final ItemKey itemKey = holder.itemKeyForBrowseSlot(slot);
            if (itemKey == null) {
                return;
            }
            this.shopMenuRouter.openDetail(player, itemKey);
            return;
        }

        switch (slot) {
            case 45 -> {
                if (page > 0) {
                    this.shopMenuRouter.openBrowse(player, layoutGroupKey, layoutChildKey, page - 1, viaSubcategory);
                }
            }
            case 49 -> this.shopMenuRouter.goBack(player);
            case 53 -> {
                final int totalVisible = this.exchangeLayoutBrowseService.countVisibleItems(layoutGroupKey, layoutChildKey);
                if ((page + 1) * 45 < totalVisible) {
                    this.shopMenuRouter.openBrowse(player, layoutGroupKey, layoutChildKey, page + 1, viaSubcategory);
                }
            }
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
        final ItemStack stack = this.exchangeItemCodec.createItemStack(view.itemKey(), 1)
            .orElseGet(() -> new ItemStack(Material.BARRIER));
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
}
