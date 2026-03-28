package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.supplier.SupplierContributionEntry;
import com.splatage.wild_economy.exchange.supplier.SupplierPlayerDetail;
import com.splatage.wild_economy.exchange.supplier.SupplierScope;
import com.splatage.wild_economy.exchange.supplier.SupplierStatsService;
import com.splatage.wild_economy.exchange.supplier.TopSupplierEntry;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class TopSupplierMenu {

    private static final int INVENTORY_SIZE = 36;

    private static final int SLOT_WEEKLY = 10;
    private static final int SLOT_TITLE = 13;
    private static final int SLOT_ALL_TIME = 16;

    private static final int[] LEADERBOARD_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25, 26};

    private static final int SLOT_PLAYER_SUMMARY = 11;
    private static final int[] DETAIL_CONTRIBUTION_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25};
    private static final int SLOT_DETAIL_RANK = 15;

    private static final int SLOT_BACK = 31;
    private static final int SLOT_CLOSE = 35;

    private static final int LEADERBOARD_LIMIT = LEADERBOARD_SLOTS.length;
    private static final int DETAIL_ITEM_LIMIT = DETAIL_CONTRIBUTION_SLOTS.length;

    private final SupplierStatsService supplierStatsService;
    private final PlayerInfoItemFactory playerInfoItemFactory;
    private ShopMenuRouter shopMenuRouter;

    public TopSupplierMenu(
        final SupplierStatsService supplierStatsService,
        final PlayerInfoItemFactory playerInfoItemFactory
    ) {
        this.supplierStatsService = Objects.requireNonNull(supplierStatsService, "supplierStatsService");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player) {
        this.open(player, SupplierScope.WEEKLY);
    }

    public void open(final Player player, final SupplierScope scope) {
        final TopSupplierMenuHolder holder = TopSupplierMenuHolder.leaderboard(scope);
        final Inventory inventory = holder.createInventory(INVENTORY_SIZE, "Shop - Top Suppliers");

        this.decorateCommonFrame(inventory, player, scope);
        inventory.setItem(SLOT_TITLE, this.infoItem(
            Material.GOLD_INGOT,
            "Top Suppliers",
            List.of(
                "§7The players supplying the most",
                "§7items into the shared economy.",
                "",
                "§eClick a player to view details"
            )
        ));

        final List<TopSupplierEntry> entries = this.supplierStatsService.getTopSuppliers(scope, LEADERBOARD_LIMIT);
        if (entries.isEmpty()) {
            inventory.setItem(22, this.infoItem(
                Material.PAPER,
                "No Supplier Data",
                List.of(
                    "§7No supplier data has been recorded",
                    "§7for this scope yet."
                )
            ));
        } else {
            for (int i = 0; i < entries.size() && i < LEADERBOARD_SLOTS.length; i++) {
                inventory.setItem(LEADERBOARD_SLOTS[i], this.leaderboardEntry(entries.get(i)));
            }
        }

        player.openInventory(inventory);
    }

    public void openPlayerDetail(final Player viewer, final UUID playerId, final SupplierScope scope) {
        final Optional<SupplierPlayerDetail> detail = this.supplierStatsService.getPlayerDetail(scope, playerId, DETAIL_ITEM_LIMIT);
        if (detail.isEmpty()) {
            this.open(viewer, scope);
            return;
        }

        final SupplierPlayerDetail supplierPlayerDetail = detail.get();
        final TopSupplierMenuHolder holder = TopSupplierMenuHolder.detail(scope, playerId);
        final Inventory inventory = holder.createInventory(INVENTORY_SIZE, "Shop - Supplier Detail");

        this.decorateCommonFrame(inventory, viewer, scope);
        inventory.setItem(SLOT_TITLE, this.infoItem(
            Material.BOOK,
            supplierPlayerDetail.displayName(),
            List.of(
                "§7Contribution detail for this scope.",
                "",
                "§7Total supplied: §a" + formatQuantity(supplierPlayerDetail.totalQuantitySold()) + " items"
            )
        ));
        inventory.setItem(SLOT_PLAYER_SUMMARY, this.playerSummary(supplierPlayerDetail));
        inventory.setItem(SLOT_DETAIL_RANK, this.rankSummary(supplierPlayerDetail));

        if (supplierPlayerDetail.topContributions().isEmpty()) {
            inventory.setItem(22, this.infoItem(
                Material.PAPER,
                "No Contributions",
                List.of("§7No item contribution details found for this scope.")
            ));
        } else {
            for (int i = 0; i < supplierPlayerDetail.topContributions().size() && i < DETAIL_CONTRIBUTION_SLOTS.length; i++) {
                inventory.setItem(DETAIL_CONTRIBUTION_SLOTS[i], this.contributionEntry(i + 1, supplierPlayerDetail.topContributions().get(i)));
            }
        }

        viewer.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final TopSupplierMenuHolder holder = this.resolveHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot == SLOT_WEEKLY) {
            if (holder.selectedPlayerId() == null) {
                this.open(player, SupplierScope.WEEKLY);
            } else {
                this.openPlayerDetail(player, holder.selectedPlayerId(), SupplierScope.WEEKLY);
            }
            return;
        }

        if (rawSlot == SLOT_ALL_TIME) {
            if (holder.selectedPlayerId() == null) {
                this.open(player, SupplierScope.ALL_TIME);
            } else {
                this.openPlayerDetail(player, holder.selectedPlayerId(), SupplierScope.ALL_TIME);
            }
            return;
        }

        if (rawSlot == SLOT_BACK) {
            if (holder.selectedPlayerId() == null) {
                this.shopMenuRouter.openRoot(player);
            } else {
                this.open(player, holder.scope());
            }
            return;
        }

        if (rawSlot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (holder.selectedPlayerId() == null) {
            final List<TopSupplierEntry> entries = this.supplierStatsService.getTopSuppliers(holder.scope(), LEADERBOARD_LIMIT);
            for (int i = 0; i < entries.size() && i < LEADERBOARD_SLOTS.length; i++) {
                if (rawSlot == LEADERBOARD_SLOTS[i]) {
                    this.openPlayerDetail(player, entries.get(i).playerId(), holder.scope());
                    return;
                }
            }
        }
    }

    public boolean isTopSupplierInventory(final Inventory inventory) {
        return this.resolveHolder(inventory) != null;
    }

    private void decorateCommonFrame(final Inventory inventory, final Player viewer, final SupplierScope scope) {
        inventory.setItem(SLOT_WEEKLY, this.scopeButton("Weekly", Material.CLOCK, scope == SupplierScope.WEEKLY));
        inventory.setItem(SLOT_ALL_TIME, this.scopeButton("All Time", Material.GOLD_BLOCK, scope == SupplierScope.ALL_TIME));
        inventory.setItem(30, this.playerInfoItemFactory.create(viewer));
        inventory.setItem(SLOT_BACK, this.navButton(Material.ARROW, "Back"));
        inventory.setItem(SLOT_CLOSE, this.navButton(Material.BARRIER, "Close"));
    }

    private ItemStack scopeButton(final String label, final Material material, final boolean active) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(active ? "§a" + label : "§e" + label);
            meta.setLore(List.of(
                active ? "§7Currently selected" : "§7Click to switch to " + label.toLowerCase(Locale.ROOT),
                "",
                active ? "§aActive" : "§eView this scope"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack leaderboardEntry(final TopSupplierEntry entry) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.playerId());
        final ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName(this.rankColor(entry.rank()) + "#" + entry.rank() + " §e" + entry.displayName());
            meta.setLore(List.of(
                "§7Total supplied:",
                "§a" + formatQuantity(entry.totalQuantitySold()) + " items",
                "",
                "§eClick to view contribution details"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack playerSummary(final SupplierPlayerDetail detail) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(detail.playerId());
        final ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName("§6" + detail.displayName());
            meta.setLore(List.of(
                "§7Total supplied:",
                "§a" + formatQuantity(detail.totalQuantitySold()) + " items",
                "",
                "§7Scope: §e" + scopeLabel(detail.scope())
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack rankSummary(final SupplierPlayerDetail detail) {
        return this.infoItem(
            Material.NAME_TAG,
            "Rank #" + detail.rank(),
            List.of(
                "§7Leaderboard position in this scope.",
                "",
                "§7Current scope: §e" + scopeLabel(detail.scope())
            )
        );
    }

    private ItemStack contributionEntry(final int position, final SupplierContributionEntry contribution) {
        return this.infoItem(
            Material.CHEST,
            "#" + position + " " + contribution.displayName(),
            List.of(
                "§7Quantity supplied:",
                "§a" + formatQuantity(contribution.quantitySold()),
                "",
                "§7Item key: §8" + contribution.itemKey().value()
            )
        );
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

    private String rankColor(final int rank) {
        return switch (rank) {
            case 1 -> "§6";
            case 2 -> "§e";
            case 3 -> "§7";
            default -> "§f";
        };
    }

    private static String scopeLabel(final SupplierScope scope) {
        return scope == SupplierScope.ALL_TIME ? "All Time" : "Weekly";
    }

    private static String formatQuantity(final long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private TopSupplierMenuHolder resolveHolder(final Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        final InventoryHolder holder = inventory.getHolder();
        if (holder instanceof TopSupplierMenuHolder topSupplierMenuHolder) {
            return topSupplierMenuHolder;
        }
        return null;
    }

    private static final class TopSupplierMenuHolder implements InventoryHolder {

        private final SupplierScope scope;
        private final UUID selectedPlayerId;
        private Inventory inventory;

        private TopSupplierMenuHolder(final SupplierScope scope, final UUID selectedPlayerId) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.selectedPlayerId = selectedPlayerId;
        }

        private static TopSupplierMenuHolder leaderboard(final SupplierScope scope) {
            return new TopSupplierMenuHolder(scope, null);
        }

        private static TopSupplierMenuHolder detail(final SupplierScope scope, final UUID playerId) {
            return new TopSupplierMenuHolder(scope, Objects.requireNonNull(playerId, "playerId"));
        }

        private Inventory createInventory(final int size, final String title) {
            this.inventory = Bukkit.createInventory(this, size, title);
            return this.inventory;
        }

        @Override
        public Inventory getInventory() {
            if (this.inventory == null) {
                throw new IllegalStateException("Inventory has not been created for this holder yet");
            }
            return this.inventory;
        }

        private SupplierScope scope() {
            return this.scope;
        }

        private UUID selectedPlayerId() {
            return this.selectedPlayerId;
        }
    }
}
