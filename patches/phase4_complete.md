# wild_economy — Commit 4 Complete Files

## Status

This document redoes **Commit 4** as a **complete-file set**, not partial patches.

It includes full contents for all files touched by Commit 4:

* `MenuSession.java`
* `ShopMenuRouter.java`
* `ExchangeRootMenu.java`
* `ExchangeBrowseMenu.java`
* `ExchangeItemDetailMenu.java`
* `ShopMenuListener.java`
* `StockTurnoverServiceImpl.java`
* MySQL repository implementations
* `PluginBootstrap.java`
* `WildEconomyPlugin.java`
* `ShopAdminCommand.java`
* `ServiceRegistry.java`

These contents assume the earlier Commit 1–3 files already exist.

---

## File: `src/main/java/com/splatage/wild_economy/gui/MenuSession.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.UUID;

public record MenuSession(
    UUID playerId,
    ViewType viewType,
    ItemCategory currentCategory,
    int currentPage,
    ItemKey currentItemKey
) {
    public enum ViewType {
        ROOT,
        BROWSE,
        DETAIL
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuRouter.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class ShopMenuRouter {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final Map<UUID, MenuSession> sessions = new HashMap<>();

    public ShopMenuRouter(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    public void openRoot(final Player player) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.ROOT,
            null,
            0,
            null
        ));
        this.exchangeRootMenu.open(player);
    }

    public void openBrowse(final Player player, final ItemCategory category, final int page) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.BROWSE,
            category,
            page,
            null
        ));
        this.exchangeBrowseMenu.open(player, category, page);
    }

    public void openDetail(final Player player, final ItemKey itemKey) {
        final MenuSession previous = this.sessions.get(player.getUniqueId());
        final ItemCategory category = previous == null ? null : previous.currentCategory();
        final int page = previous == null ? 0 : previous.currentPage();

        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.DETAIL,
            category,
            page,
            itemKey
        ));
        this.exchangeItemDetailMenu.open(player, itemKey, 1);
    }

    public void goBack(final Player player) {
        final MenuSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            this.openRoot(player);
            return;
        }

        switch (session.viewType()) {
            case ROOT -> this.openRoot(player);
            case BROWSE -> this.openRoot(player);
            case DETAIL -> {
                if (session.currentCategory() == null) {
                    this.openRoot(player);
                } else {
                    this.openBrowse(player, session.currentCategory(), session.currentPage());
                }
            }
        }
    }

    public MenuSession getSession(final UUID playerId) {
        return this.sessions.get(playerId);
    }

    public void clearSession(final UUID playerId) {
        this.sessions.remove(playerId);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeRootMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeRootMenu {

    private ShopMenuRouter shopMenuRouter;

    public ExchangeRootMenu() {
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player) {
        final Inventory inventory = Bukkit.createInventory(null, 27, "Shop");
        inventory.setItem(10, this.button(Material.WHEAT, "Farming"));
        inventory.setItem(11, this.button(Material.IRON_PICKAXE, "Mining"));
        inventory.setItem(12, this.button(Material.BONE, "Mob Drops"));
        inventory.setItem(14, this.button(Material.BRICKS, "Building"));
        inventory.setItem(15, this.button(Material.CHEST, "Utility"));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> this.shopMenuRouter.openBrowse(player, ItemCategory.FARMING, 0);
            case 11 -> this.shopMenuRouter.openBrowse(player, ItemCategory.MINING, 0);
            case 12 -> this.shopMenuRouter.openBrowse(player, ItemCategory.MOB_DROPS, 0);
            case 14 -> this.shopMenuRouter.openBrowse(player, ItemCategory.BUILDING, 0);
            case 15 -> this.shopMenuRouter.openBrowse(player, ItemCategory.UTILITY, 0);
            case 22 -> player.closeInventory();
            default -> {
            }
        }
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
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeBrowseMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.service.ExchangeCatalogView;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeBrowseMenu {

    private final ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeBrowseMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player, final ItemCategory category, final int page) {
        final Inventory inventory = Bukkit.createInventory(null, 54, "Shop - " + this.prettyCategory(category));
        final List<ExchangeCatalogView> entries = this.exchangeService.browseCategory(category, page, 45);

        int slot = 0;
        for (final ExchangeCatalogView view : entries) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.catalogItem(view));
            slot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.button(Material.ARROW, "Next"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final ItemCategory category, final int page) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta() || !clicked.getItemMeta().hasLocalizedName()) {
                return;
            }
            final String itemKeyValue = clicked.getItemMeta().getLocalizedName();
            this.shopMenuRouter.openDetail(player, new ItemKey(itemKeyValue));
            return;
        }

        switch (slot) {
            case 45 -> this.shopMenuRouter.openRoot(player);
            case 49 -> player.closeInventory();
            case 53 -> this.shopMenuRouter.openBrowse(player, category, page + 1);
            default -> {
            }
        }
    }

    private ItemStack catalogItem(final ExchangeCatalogView view) {
        final Material material = this.resolveMaterial(view.itemKey());
        final ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(view.displayName());
            meta.setLocalizedName(view.itemKey().value());
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

    private String prettyCategory(final ItemCategory category) {
        return switch (category) {
            case FARMING -> "Farming";
            case MINING -> "Mining";
            case MOB_DROPS -> "Mob Drops";
            case BUILDING -> "Building";
            case UTILITY -> "Utility";
        };
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeItemDetailMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.service.ExchangeItemView;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeItemDetailMenu {

    private final ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeItemDetailMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player, final ItemKey itemKey, final int amount) {
        final ExchangeItemView view = this.exchangeService.getItemView(itemKey);
        final Inventory inventory = Bukkit.createInventory(null, 27, "Buy - " + view.displayName());

        inventory.setItem(11, this.detailItem(view, amount));
        inventory.setItem(13, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 1"));
        inventory.setItem(14, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 8"));
        inventory.setItem(15, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 64"));
        inventory.setItem(18, this.button(Material.ARROW, "Back"));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final ItemKey itemKey) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot == 18) {
            this.shopMenuRouter.goBack(player);
            return;
        }
        if (slot == 22) {
            player.closeInventory();
            return;
        }

        final int amount = switch (slot) {
            case 13 -> 1;
            case 14 -> 8;
            case 15 -> 64;
            default -> 0;
        };

        if (amount > 0) {
            final BuyResult result = this.exchangeService.buy(player.getUniqueId(), itemKey, amount);
            player.sendMessage(result.message());
            if (result.success()) {
                this.open(player, itemKey, 1);
            }
        }
    }

    private ItemStack detailItem(final ExchangeItemView view, final int amount) {
        final Material material = this.resolveMaterial(view.itemKey());
        final ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material, Math.max(1, Math.min(amount, 64)));
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(view.displayName());
            meta.setLocalizedName(view.itemKey().value());
            meta.setLore(List.of(
                "Unit price: " + view.buyPrice(),
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
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuListener.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class ShopMenuListener implements Listener {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final ShopMenuRouter shopMenuRouter;

    public ShopMenuListener(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu,
        final ShopMenuRouter shopMenuRouter
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null) {
            return;
        }

        if (title.equals("Shop")) {
            this.exchangeRootMenu.handleClick(event);
            return;
        }

        if (title.startsWith("Shop - ")) {
            final MenuSession session = this.shopMenuRouter.getSession(event.getWhoClicked().getUniqueId());
            final ItemCategory category = session != null && session.currentCategory() != null
                ? session.currentCategory()
                : this.parseCategory(title.substring("Shop - ".length()));
            final int page = session == null ? 0 : session.currentPage();
            if (category != null) {
                this.exchangeBrowseMenu.handleClick(event, category, page);
            }
            return;
        }

        if (title.startsWith("Buy - ")) {
            final MenuSession session = this.shopMenuRouter.getSession(event.getWhoClicked().getUniqueId());
            if (session != null && session.currentItemKey() != null) {
                this.exchangeItemDetailMenu.handleClick(event, session.currentItemKey());
                return;
            }

            final var current = event.getInventory().getItem(11);
            if (current != null && current.hasItemMeta() && current.getItemMeta().hasLocalizedName()) {
                this.exchangeItemDetailMenu.handleClick(event, new ItemKey(current.getItemMeta().getLocalizedName()));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        final String title = event.getView().getTitle();
        if (title != null && (title.equals("Shop") || title.startsWith("Shop - ") || title.startsWith("Buy - "))) {
            this.shopMenuRouter.clearSession(event.getPlayer().getUniqueId());
        }
    }

    private ItemCategory parseCategory(final String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "farming" -> ItemCategory.FARMING;
            case "mining" -> ItemCategory.MINING;
            case "mob drops" -> ItemCategory.MOB_DROPS;
            case "building" -> ItemCategory.BUILDING;
            case "utility" -> ItemCategory.UTILITY;
            default -> null;
        };
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockTurnoverServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import java.util.Objects;

public final class StockTurnoverServiceImpl implements StockTurnoverService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;
    private final TransactionLogService transactionLogService;

    public StockTurnoverServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService,
        final TransactionLogService transactionLogService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.transactionLogService = Objects.requireNonNull(transactionLogService, "transactionLogService");
    }

    @Override
    public void runTurnoverPass() {
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.allEntries()) {
            if (entry.policyMode() != ItemPolicyMode.PLAYER_STOCKED) {
                continue;
            }
            final long turnover = Math.max(0L, entry.turnoverAmountPerInterval());
            if (turnover <= 0L) {
                continue;
            }

            final var snapshot = this.stockService.getSnapshot(entry.itemKey());
            if (snapshot.stockCount() <= 0L) {
                continue;
            }

            final int removeAmount = (int) Math.min(snapshot.stockCount(), turnover);
            if (removeAmount <= 0) {
                continue;
            }

            this.stockService.removeStock(entry.itemKey(), removeAmount);
            this.transactionLogService.logTurnover(entry.itemKey(), removeAmount);
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/mysql/MysqlSchemaVersionRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.mysql;

import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

public final class MysqlSchemaVersionRepository implements SchemaVersionRepository {

    private final DatabaseProvider databaseProvider;

    public MysqlSchemaVersionRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public int getCurrentVersion() {
        final String sql = "SELECT version FROM schema_version ORDER BY version DESC LIMIT 1";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("version");
            }
            return 0;
        } catch (final SQLException exception) {
            return 0;
        }
    }

    @Override
    public void setCurrentVersion(final int version) {
        final String deleteSql = "DELETE FROM schema_version";
        final String insertSql = "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)";

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                deleteStatement.executeUpdate();
            }

            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                insertStatement.setInt(1, version);
                insertStatement.setLong(2, Instant.now().getEpochSecond());
                insertStatement.executeUpdate();
            }

            connection.commit();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to set schema version to " + version, exception);
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/mysql/MysqlExchangeStockRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.mysql;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class MysqlExchangeStockRepository implements ExchangeStockRepository {

    private final DatabaseProvider databaseProvider;

    public MysqlExchangeStockRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public long getStock(final ItemKey itemKey) {
        this.ensureRowExists(itemKey);
        final String sql = "SELECT stock_count FROM exchange_stock WHERE item_key = ?";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemKey.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("stock_count");
                }
                return 0L;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load stock for " + itemKey.value(), exception);
        }
    }

    @Override
    public Map<ItemKey, Long> getStocks(final Iterable<ItemKey> itemKeys) {
        final Map<ItemKey, Long> result = new LinkedHashMap<>();
        for (final ItemKey itemKey : itemKeys) {
            result.put(itemKey, this.getStock(itemKey));
        }
        return result;
    }

    @Override
    public void incrementStock(final ItemKey itemKey, final int amount) {
        this.changeStock(itemKey, amount);
    }

    @Override
    public void decrementStock(final ItemKey itemKey, final int amount) {
        this.changeStock(itemKey, -amount);
    }

    @Override
    public void setStock(final ItemKey itemKey, final long stock) {
        this.ensureRowExists(itemKey);
        final String sql = "UPDATE exchange_stock SET stock_count = ?, updated_at = ? WHERE item_key = ?";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Math.max(0L, stock));
            statement.setLong(2, Instant.now().getEpochSecond());
            statement.setString(3, itemKey.value());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to set stock for " + itemKey.value(), exception);
        }
    }

    private void changeStock(final ItemKey itemKey, final int delta) {
        this.ensureRowExists(itemKey);
        final long current = this.getStock(itemKey);
        final long updated = Math.max(0L, current + delta);
        this.setStock(itemKey, updated);
    }

    private void ensureRowExists(final ItemKey itemKey) {
        final String sql = "INSERT IGNORE INTO exchange_stock (item_key, stock_count, updated_at) VALUES (?, ?, ?)";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemKey.value());
            statement.setLong(2, 0L);
            statement.setLong(3, Instant.now().getEpochSecond());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to ensure stock row for " + itemKey.value(), exception);
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/mysql/MysqlExchangeTransactionRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.mysql;

import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.JdbcUtils;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MysqlExchangeTransactionRepository implements ExchangeTransactionRepository {

    private final DatabaseProvider databaseProvider;

    public MysqlExchangeTransactionRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public void insert(
        final TransactionType type,
        final UUID playerId,
        final String itemKey,
        final int amount,
        final BigDecimal unitPrice,
        final BigDecimal totalValue,
        final Instant createdAt,
        final String metaJson
    ) {
        final String sql = "INSERT INTO exchange_transactions "
            + "(transaction_type, player_uuid, item_key, amount, unit_price, total_value, created_at, meta_json) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, playerId.toString());
            statement.setString(3, itemKey);
            statement.setInt(4, amount);
            statement.setBigDecimal(5, unitPrice);
            statement.setBigDecimal(6, totalValue);
            statement.setLong(7, createdAt.getEpochSecond());
            JdbcUtils.bindNullableString(statement, 8, metaJson);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to insert exchange transaction", exception);
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/bootstrap/PluginBootstrap.java`

```java
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;

public final class PluginBootstrap {

    private final WildEconomyPlugin plugin;
    private ServiceRegistry services;

    public PluginBootstrap(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.services = new ServiceRegistry(this.plugin);
        this.services.initialize();
        this.services.registerCommands();
        this.services.registerTasks();
    }

    public void reload() {
        if (this.services != null) {
            this.services.shutdown();
        }
        this.enable();
    }

    public void disable() {
        if (this.services != null) {
            this.services.shutdown();
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/WildEconomyPlugin.java`

```java
package com.splatage.wild_economy;

import com.splatage.wild_economy.bootstrap.PluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

public final class WildEconomyPlugin extends JavaPlugin {

    private PluginBootstrap bootstrap;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.saveResource("database.yml", false);
        this.saveResource("exchange-items.yml", false);
        this.saveResource("worth-import.yml", false);
        this.saveResource("messages.yml", false);

        this.bootstrap = new PluginBootstrap(this);
        this.bootstrap.enable();
    }

    public PluginBootstrap getBootstrap() {
        return this.bootstrap;
    }

    @Override
    public void onDisable() {
        if (this.bootstrap != null) {
            this.bootstrap.disable();
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/command/ShopAdminCommand.java`

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.WildEconomyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopAdminCommand implements CommandExecutor {

    private final WildEconomyPlugin plugin;

    public ShopAdminCommand(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Use /shopadmin reload");
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            this.plugin.reloadConfig();
            this.plugin.getBootstrap().reload();
            sender.sendMessage("wild_economy reloaded.");
            return true;
        }

        sender.sendMessage("Unknown admin subcommand.");
        return true;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

```java
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.command.ShopOpenSubcommand;
import com.splatage.wild_economy.command.ShopSellAllSubcommand;
import com.splatage.wild_economy.command.ShopSellHandSubcommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.VaultEconomyGateway;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.CatalogMergeService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.WorthImporter;
import com.splatage.wild_economy.exchange.item.BukkitItemNormalizer;
import com.splatage.wild_economy.exchange.item.CanonicalItemRules;
import com.splatage.wild_economy.exchange.item.ItemNormalizer;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.item.ItemValidationServiceImpl;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.pricing.PricingServiceImpl;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlSchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteSchemaVersionRepository;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseService;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeBuyService;
import com.splatage.wild_economy.exchange.service.ExchangeBuyServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeSellService;
import com.splatage.wild_economy.exchange.service.ExchangeSellServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.exchange.service.ExchangeServiceImpl;
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import com.splatage.wild_economy.exchange.service.TransactionLogServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.exchange.stock.StockServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockStateResolver;
import com.splatage.wild_economy.exchange.stock.StockTurnoverService;
import com.splatage.wild_economy.exchange.stock.StockTurnoverServiceImpl;
import com.splatage.wild_economy.gui.ExchangeBrowseMenu;
import com.splatage.wild_economy.gui.ExchangeItemDetailMenu;
import com.splatage.wild_economy.gui.ExchangeRootMenu;
import com.splatage.wild_economy.gui.ShopMenuListener;
import com.splatage.wild_economy.gui.ShopMenuRouter;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
    private WorthImportConfig worthImportConfig;
    private ExchangeItemsConfig exchangeItemsConfig;

    private DatabaseProvider databaseProvider;
    private ExchangeCatalog exchangeCatalog;
    private ItemNormalizer itemNormalizer;
    private ItemValidationService itemValidationService;
    private ExchangeStockRepository exchangeStockRepository;
    private ExchangeTransactionRepository exchangeTransactionRepository;
    private EconomyGateway economyGateway;

    private StockService stockService;
    private PricingService pricingService;
    private TransactionLogService transactionLogService;
    private StockTurnoverService stockTurnoverService;
    private ExchangeBrowseService exchangeBrowseService;
    private ExchangeBuyService exchangeBuyService;
    private ExchangeSellService exchangeSellService;
    private ExchangeService exchangeService;

    private ShopMenuRouter shopMenuRouter;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        final ConfigLoader configLoader = new ConfigLoader(this.plugin);
        this.globalConfig = configLoader.loadGlobalConfig();
        this.databaseConfig = configLoader.loadDatabaseConfig();
        this.worthImportConfig = configLoader.loadWorthImportConfig();
        this.exchangeItemsConfig = configLoader.loadExchangeItemsConfig();

        this.databaseProvider = new DatabaseProvider(this.databaseConfig);

        final SchemaVersionRepository schemaVersionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(this.databaseProvider);
            case MYSQL -> new MysqlSchemaVersionRepository(this.databaseProvider);
        };

        final MigrationManager migrationManager = new MigrationManager(this.databaseProvider, schemaVersionRepository);
        migrationManager.migrate();

        this.exchangeStockRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(this.databaseProvider);
            case MYSQL -> new MysqlExchangeStockRepository(this.databaseProvider);
        };

        this.exchangeTransactionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(this.databaseProvider);
            case MYSQL -> new MysqlExchangeTransactionRepository(this.databaseProvider);
        };

        final WorthImporter worthImporter = new WorthImporter();
        final CatalogMergeService catalogMergeService = new CatalogMergeService();
        final CatalogLoader catalogLoader = new CatalogLoader(worthImporter, catalogMergeService);
        this.exchangeCatalog = catalogLoader.load(this.exchangeItemsConfig, this.worthImportConfig);

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        this.itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        this.itemValidationService = new ItemValidationServiceImpl(this.itemNormalizer, this.exchangeCatalog);

        this.economyGateway = this.resolveVaultEconomy();

        final StockStateResolver stockStateResolver = new StockStateResolver();
        this.stockService = new StockServiceImpl(this.exchangeStockRepository, this.exchangeCatalog, stockStateResolver);
        this.pricingService = new PricingServiceImpl(this.exchangeCatalog);
        this.transactionLogService = new TransactionLogServiceImpl(this.exchangeTransactionRepository);
        this.stockTurnoverService = new StockTurnoverServiceImpl(
            this.exchangeCatalog,
            this.stockService,
            this.transactionLogService
        );
        this.exchangeBrowseService = new ExchangeBrowseServiceImpl(this.exchangeCatalog, this.stockService);
        this.exchangeBuyService = new ExchangeBuyServiceImpl(
            this.exchangeCatalog,
            this.itemValidationService,
            this.stockService,
            this.pricingService,
            this.economyGateway,
            this.transactionLogService
        );
        this.exchangeSellService = new ExchangeSellServiceImpl(
            this.exchangeCatalog,
            this.itemValidationService,
            this.stockService,
            this.pricingService,
            this.economyGateway,
            this.transactionLogService
        );
        this.exchangeService = new ExchangeServiceImpl(
            this.exchangeBrowseService,
            this.exchangeBuyService,
            this.exchangeSellService
        );

        final ExchangeRootMenu rootMenu = new ExchangeRootMenu();
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService);
        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService);
        this.shopMenuRouter = new ShopMenuRouter(rootMenu, browseMenu, itemDetailMenu);

        rootMenu.setShopMenuRouter(this.shopMenuRouter);
        browseMenu.setShopMenuRouter(this.shopMenuRouter);
        itemDetailMenu.setShopMenuRouter(this.shopMenuRouter);

        this.plugin.getServer().getPluginManager().registerEvents(
            new ShopMenuListener(rootMenu, browseMenu, itemDetailMenu, this.shopMenuRouter),
            this.plugin
        );
    }

    public void registerCommands() {
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(
                new ShopOpenSubcommand(this.shopMenuRouter),
                new ShopSellHandSubcommand(this.exchangeService),
                new ShopSellAllSubcommand(this.exchangeService)
            ));
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand(this.plugin));
        }
    }

    public void registerTasks() {
        this.plugin.getServer().getScheduler().runTaskTimer(
            this.plugin,
            new com.splatage.wild_economy.scheduler.StockTurnoverTask(this.stockTurnoverService),
            this.globalConfig.turnoverIntervalTicks(),
            this.globalConfig.turnoverIntervalTicks()
        );
    }

    public void shutdown() {
        this.plugin.getServer().getScheduler().cancelTasks(this.plugin);
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }
        return new VaultEconomyGateway(registration.getProvider());
    }
}
```
