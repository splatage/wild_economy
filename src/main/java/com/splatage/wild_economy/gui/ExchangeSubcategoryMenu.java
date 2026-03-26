package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.service.ExchangeService;
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
    private ShopMenuRouter shopMenuRouter;

    public ExchangeSubcategoryMenu(
        final ExchangeService exchangeService,
        final PlayerInfoItemFactory playerInfoItemFactory
    ) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player, final ItemCategory category) {
        final ShopMenuHolder holder = ShopMenuHolder.subcategory(category);
        final Inventory inventory = holder.createInventory(27, "Shop - " + category.displayName() + " Types");

        inventory.setItem(21, this.playerInfoItemFactory.create(player));
        inventory.setItem(10, this.button(Material.CHEST, "All"));

        final List<GeneratedItemCategory> subcategories = this.exchangeService.listVisibleSubcategories(category);
        for (int i = 0; i < subcategories.size() && i < SUBCATEGORY_SLOTS.length; i++) {
            final GeneratedItemCategory generatedItemCategory = subcategories.get(i);
            inventory.setItem(
                SUBCATEGORY_SLOTS[i],
                this.button(this.icon(generatedItemCategory), generatedItemCategory.displayName())
            );
        }

        inventory.setItem(18, this.button(Material.ARROW, "Back"));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final ItemCategory category) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot == 10) {
            this.shopMenuRouter.openBrowse(player, category, null, 0, true);
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

        final List<GeneratedItemCategory> subcategories = this.exchangeService.listVisibleSubcategories(category);
        for (int i = 0; i < subcategories.size() && i < SUBCATEGORY_SLOTS.length; i++) {
            if (SUBCATEGORY_SLOTS[i] == rawSlot) {
                this.shopMenuRouter.openBrowse(player, category, subcategories.get(i), 0, true);
                return;
            }
        }
    }

    private Material icon(final GeneratedItemCategory generatedItemCategory) {
        return switch (generatedItemCategory) {
            case FARMING -> Material.WHEAT;
            case FOOD -> Material.BREAD;
            case ORES_AND_MINERALS -> Material.IRON_INGOT;
            case MOB_DROPS -> Material.BONE;
            case WOODS -> Material.OAK_LOG;
            case STONE -> Material.STONE;
            case REDSTONE -> Material.REDSTONE;
            case TOOLS -> Material.IRON_PICKAXE;
            case BREWING -> Material.BREWING_STAND;
            case TRANSPORT -> Material.MINECART;
            case COMBAT -> Material.DIAMOND_SWORD;
            case NETHER -> Material.NETHERRACK;
            case END -> Material.END_STONE;
            case DECORATION -> Material.PAINTING;
            case MISC -> Material.CHEST;
        };
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

