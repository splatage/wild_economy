package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.activity.MarketActivityCategory;
import com.splatage.wild_economy.exchange.activity.MarketActivityItemView;
import com.splatage.wild_economy.exchange.activity.MarketActivityService;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MarketActivityMenu {

    private static final int ROOT_SIZE = 27;
    private static final int PAGE_SIZE = 54;
    private static final int[] ROOT_CATEGORY_SLOTS = {10, 12, 14, 16};
    private static final int[] PAGE_ITEM_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private final MarketActivityService marketActivityService;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private ShopMenuRouter shopMenuRouter;

    public MarketActivityMenu(
        final MarketActivityService marketActivityService,
        final PlayerInfoItemFactory playerInfoItemFactory
    ) {
        this.marketActivityService = Objects.requireNonNull(marketActivityService, "marketActivityService");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void openRoot(final Player player) {
        final MarketActivityMenuHolder holder = MarketActivityMenuHolder.root();
        final Inventory inventory = holder.createInventory(ROOT_SIZE, "Shop - Market Activity");
        inventory.setItem(ROOT_CATEGORY_SLOTS[0], this.categoryButton(MarketActivityCategory.RECENTLY_STOCKED));
        inventory.setItem(ROOT_CATEGORY_SLOTS[1], this.categoryButton(MarketActivityCategory.RECENTLY_PURCHASED));
        inventory.setItem(ROOT_CATEGORY_SLOTS[2], this.categoryButton(MarketActivityCategory.TOP_TURNOVER));
        inventory.setItem(ROOT_CATEGORY_SLOTS[3], this.categoryButton(MarketActivityCategory.YOUR_RECENT_PURCHASES));
        inventory.setItem(18, this.navButton(Material.ARROW, "Back"));
        inventory.setItem(21, this.playerInfoItemFactory.create(player));
        inventory.setItem(26, this.navButton(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void openCategory(final Player player, final MarketActivityCategory category) {
        final List<MarketActivityItemView> entries = this.marketActivityService.listItems(category, player.getUniqueId(), PAGE_ITEM_SLOTS.length);
        final MarketActivityMenuHolder holder = MarketActivityMenuHolder.category(category, entries);
        final Inventory inventory = holder.createInventory(PAGE_SIZE, "Activity - " + category.displayName());

        for (int i = 0; i < entries.size() && i < PAGE_ITEM_SLOTS.length; i++) {
            inventory.setItem(PAGE_ITEM_SLOTS[i], this.activityItem(category, entries.get(i)));
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, this.infoItem(
                Material.PAPER,
                "No Activity",
                List.of("§7No items matched this view", "§7inside the recent window.")
            ));
        }

        inventory.setItem(48, this.playerInfoItemFactory.create(player));
        inventory.setItem(49, this.navButton(Material.ARROW, "Back"));
        inventory.setItem(53, this.navButton(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public boolean isMarketActivityInventory(final Inventory inventory) {
        return this.resolveHolder(inventory) != null;
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final MarketActivityMenuHolder holder = this.resolveHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        final int rawSlot = event.getRawSlot();
        if (holder.category() == null) {
            if (rawSlot == ROOT_CATEGORY_SLOTS[0]) {
                this.openCategory(player, MarketActivityCategory.RECENTLY_STOCKED);
                return;
            }
            if (rawSlot == ROOT_CATEGORY_SLOTS[1]) {
                this.openCategory(player, MarketActivityCategory.RECENTLY_PURCHASED);
                return;
            }
            if (rawSlot == ROOT_CATEGORY_SLOTS[2]) {
                this.openCategory(player, MarketActivityCategory.TOP_TURNOVER);
                return;
            }
            if (rawSlot == ROOT_CATEGORY_SLOTS[3]) {
                this.openCategory(player, MarketActivityCategory.YOUR_RECENT_PURCHASES);
                return;
            }
            if (rawSlot == 18) {
                this.shopMenuRouter.openRoot(player);
                return;
            }
            if (rawSlot == 26) {
                player.closeInventory();
            }
            return;
        }

        if (rawSlot == 49) {
            this.openRoot(player);
            return;
        }
        if (rawSlot == 53) {
            player.closeInventory();
            return;
        }
        for (int i = 0; i < holder.entries().size() && i < PAGE_ITEM_SLOTS.length; i++) {
            if (rawSlot == PAGE_ITEM_SLOTS[i]) {
                this.shopMenuRouter.openDetail(player, holder.entries().get(i).itemKey());
                return;
            }
        }
    }

    private ItemStack categoryButton(final MarketActivityCategory category) {
        final Material material = switch (category) {
            case RECENTLY_STOCKED -> Material.CHEST;
            case RECENTLY_PURCHASED -> Material.EMERALD;
            case TOP_TURNOVER -> Material.GOLD_INGOT;
            case YOUR_RECENT_PURCHASES -> Material.BOOK;
        };
        final List<String> lore = switch (category) {
            case RECENTLY_STOCKED -> List.of("§7Freshly supplied items", "§7newest first");
            case RECENTLY_PURCHASED -> List.of("§7Items bought most recently", "§7newest first");
            case TOP_TURNOVER -> List.of("§7Highest recent value traded", "§7within the recent window");
            case YOUR_RECENT_PURCHASES -> List.of("§7Your own most recent buys", "§7useful for live projects");
        };
        return this.infoItem(material, category.displayName(), lore);
    }

    private ItemStack activityItem(final MarketActivityCategory category, final MarketActivityItemView view) {
        final Material material = this.resolveMaterial(view.itemKey());
        final List<String> lore = switch (category) {
            case RECENTLY_STOCKED -> List.of(
                "§7Last stocked:",
                "§e" + this.relativeTime(view.eventEpochSecond()),
                "",
                "§eClick to view item"
            );
            case RECENTLY_PURCHASED -> List.of(
                "§7Last purchased:",
                "§e" + this.relativeTime(view.eventEpochSecond()),
                "",
                "§eClick to view item"
            );
            case TOP_TURNOVER -> List.of(
                "§7Recent turnover value:",
                "§6$" + this.formatMoney(view.totalValue()),
                "",
                "§eClick to view item"
            );
            case YOUR_RECENT_PURCHASES -> List.of(
                "§7Purchased:",
                "§b" + view.amount(),
                "§7When:",
                "§e" + this.relativeTime(view.eventEpochSecond()),
                view.totalValue().compareTo(BigDecimal.ZERO) > 0 ? "§7Total: §6$" + this.formatMoney(view.totalValue()) : "",
                "",
                "§eClick to view item"
            );
        };
        return this.infoItem(material, view.displayName(), lore.stream().filter(s -> !s.isEmpty()).toList());
    }

    private Material resolveMaterial(final ItemKey itemKey) {
        final String raw = itemKey.value();
        final String bukkitName = raw.startsWith("minecraft:") ? raw.substring("minecraft:".length()) : raw;
        final Material material = Material.matchMaterial(bukkitName.toUpperCase(Locale.ROOT));
        return material != null ? material : Material.BUNDLE;
    }

    private String relativeTime(final long eventEpochSecond) {
        if (eventEpochSecond <= 0L) {
            return "Unknown";
        }
        final Duration duration = Duration.between(Instant.ofEpochSecond(eventEpochSecond), Instant.now());
        final long hours = Math.max(0L, duration.toHours());
        if (hours < 1L) {
            final long minutes = Math.max(1L, duration.toMinutes());
            return minutes + " minute" + (minutes == 1L ? "" : "s") + " ago";
        }
        if (hours < 48L) {
            return hours + " hour" + (hours == 1L ? "" : "s") + " ago";
        }
        final long days = Math.max(1L, hours / 24L);
        return days + " day" + (days == 1L ? "" : "s") + " ago";
    }

    private String formatMoney(final BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private ItemStack infoItem(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack navButton(final Material material, final String name) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private MarketActivityMenuHolder resolveHolder(final Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        final InventoryHolder holder = inventory.getHolder();
        if (holder instanceof MarketActivityMenuHolder marketActivityMenuHolder) {
            return marketActivityMenuHolder;
        }
        return null;
    }

    private static final class MarketActivityMenuHolder implements InventoryHolder {

        private final MarketActivityCategory category;
        private final List<MarketActivityItemView> entries;
        private Inventory inventory;

        private MarketActivityMenuHolder(final MarketActivityCategory category, final List<MarketActivityItemView> entries) {
            this.category = category;
            this.entries = entries == null ? List.of() : List.copyOf(entries);
        }

        private static MarketActivityMenuHolder root() {
            return new MarketActivityMenuHolder(null, List.of());
        }

        private static MarketActivityMenuHolder category(final MarketActivityCategory category, final List<MarketActivityItemView> entries) {
            return new MarketActivityMenuHolder(Objects.requireNonNull(category, "category"), entries);
        }

        private Inventory createInventory(final int size, final String title) {
            this.inventory = org.bukkit.Bukkit.createInventory(this, size, title);
            return this.inventory;
        }

        @Override
        public Inventory getInventory() {
            if (this.inventory == null) {
                throw new IllegalStateException("Inventory has not been created for this holder yet");
            }
            return this.inventory;
        }

        private MarketActivityCategory category() {
            return this.category;
        }

        private List<MarketActivityItemView> entries() {
            return this.entries;
        }
    }
}
