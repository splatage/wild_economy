package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import com.splatage.wild_economy.gui.layout.LayoutChildDefinition;
import com.splatage.wild_economy.gui.layout.LayoutGroupDefinition;
import com.splatage.wild_economy.gui.layout.LayoutIconResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeSubcategoryMenu {

    private static final int[] SUBCATEGORY_SLOTS = {11, 12, 13, 14, 15, 19, 20, 23, 24};

    private final ExchangeService exchangeService;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private final LayoutBlueprint layoutBlueprint;
    private final LayoutIconResolver layoutIconResolver;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeSubcategoryMenu(
        final ExchangeService exchangeService,
        final PlayerInfoItemFactory playerInfoItemFactory,
        final LayoutBlueprint layoutBlueprint,
        final LayoutIconResolver layoutIconResolver
    ) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
        this.layoutBlueprint = Objects.requireNonNull(layoutBlueprint, "layoutBlueprint");
        this.layoutIconResolver = Objects.requireNonNull(layoutIconResolver, "layoutIconResolver");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player, final String layoutGroupKey) {
        final LayoutGroupDefinition group = this.layoutBlueprint.group(layoutGroupKey)
            .orElseThrow(() -> new IllegalStateException("Unknown layout group: " + layoutGroupKey));

        final ShopMenuHolder holder = ShopMenuHolder.subcategory(layoutGroupKey);
        final Inventory inventory = holder.createInventory(27, "Shop - " + group.label() + " Types");

        inventory.setItem(21, this.playerInfoItemFactory.create(player));
        inventory.setItem(10, this.button(Material.CHEST, "All"));

        final List<LayoutChildDefinition> children = this.visibleChildren(layoutGroupKey);
        for (int i = 0; i < children.size() && i < SUBCATEGORY_SLOTS.length; i++) {
            final LayoutChildDefinition child = children.get(i);
            inventory.setItem(
                SUBCATEGORY_SLOTS[i],
                this.button(this.layoutIconResolver.resolveChildIcon(child), child.label())
            );
        }

        inventory.setItem(18, this.button(Material.ARROW, "Back"));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final String layoutGroupKey) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot == 10) {
            this.shopMenuRouter.openBrowse(player, layoutGroupKey, null, 0, true);
            return;
        }

        if (rawSlot == 18) {
            this.shopMenuRouter.openRoot(player);
            return;
        }

        if (rawSlot == 22) {
            player.closeInventory();
            return;
        }

        final List<LayoutChildDefinition> children = this.visibleChildren(layoutGroupKey);
        for (int i = 0; i < children.size() && i < SUBCATEGORY_SLOTS.length; i++) {
            if (SUBCATEGORY_SLOTS[i] == rawSlot) {
                this.shopMenuRouter.openBrowse(player, layoutGroupKey, children.get(i).key(), 0, true);
                return;
            }
        }
    }

    private List<LayoutChildDefinition> visibleChildren(final String layoutGroupKey) {
        final List<String> visibleChildKeys = this.exchangeService.listVisibleChildKeys(layoutGroupKey);
        final List<LayoutChildDefinition> visibleChildren = new ArrayList<>();
        for (final LayoutChildDefinition child : this.layoutBlueprint.orderedChildren(layoutGroupKey)) {
            if (visibleChildKeys.contains(child.key())) {
                visibleChildren.add(child);
            }
        }
        return List.copyOf(visibleChildren);
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
