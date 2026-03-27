package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
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

public final class ExchangeRootMenu {
    private static final int[] GROUP_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final PlayerInfoItemFactory playerInfoItemFactory;
    private final ExchangeService exchangeService;
    private final LayoutBlueprint layoutBlueprint;
    private final LayoutIconResolver layoutIconResolver;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeRootMenu(
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

    public void open(final Player player) {
        final ShopMenuHolder holder = ShopMenuHolder.root();
        final Inventory inventory = holder.createInventory(27, "Shop");

        inventory.setItem(21, this.playerInfoItemFactory.create(player));

        final List<LayoutGroupDefinition> groups = this.visibleGroups();
        for (int i = 0; i < groups.size() && i < GROUP_SLOTS.length; i++) {
            final LayoutGroupDefinition group = groups.get(i);
            inventory.setItem(
                GROUP_SLOTS[i],
                this.button(this.layoutIconResolver.resolveGroupIcon(group), group.label())
            );
        }

        inventory.setItem(22, this.button(Material.NETHER_STAR, "Store"));
        inventory.setItem(26, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final List<LayoutGroupDefinition> groups = this.visibleGroups();
        for (int i = 0; i < groups.size() && i < GROUP_SLOTS.length; i++) {
            if (event.getRawSlot() == GROUP_SLOTS[i]) {
                this.openGroup(player, groups.get(i).key());
                return;
            }
        }

        switch (event.getRawSlot()) {
            case 22 -> this.shopMenuRouter.openStoreRoot(player);
            case 26 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void openGroup(final Player player, final String layoutGroupKey) {
        final int visibleCount = this.exchangeService.countVisibleItems(layoutGroupKey, null);
        final List<String> visibleChildren = this.exchangeService.listVisibleChildKeys(layoutGroupKey);

        if (visibleCount > 45 && visibleChildren.size() > 1) {
            this.shopMenuRouter.openSubcategory(player, layoutGroupKey);
            return;
        }

        this.shopMenuRouter.openBrowse(player, layoutGroupKey, null, 0, false);
    }

    private List<LayoutGroupDefinition> visibleGroups() {
        final List<LayoutGroupDefinition> groups = new ArrayList<>();
        for (final LayoutGroupDefinition group : this.layoutBlueprint.orderedGroups()) {
            if (this.exchangeService.countVisibleItems(group.key(), null) > 0) {
                groups.add(group);
            }
        }
        return List.copyOf(groups);
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
