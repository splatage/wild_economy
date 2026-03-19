# wild_economy — Commit 4 Copy-Ready Files (Hardening and Cleanup)

## Status

This document contains **copy-ready contents** for **Commit 4**.

Commit 4 scope:

* proper menu back navigation/session tracking cleanup
* actual turnover task execution
* MySQL repository implementations
* real runtime reload of config/catalog/services
* edge-case and operational hardening

This commit is about turning the current working slices into a sturdier v1 base.

---

## Scope boundaries

## Included

* stronger menu/session routing
* actual `StockTurnoverServiceImpl`
* actual scheduled turnover task registration
* MySQL repository implementations matching SQLite behavior
* runtime service rebuild on `/shopadmin reload`
* better command and inventory edge-case handling

## Deferred

* fancy pagination UX
* async DB refactor in click paths
* admin GUI
* analytics screens
* Marketplace work

---

# 1. Update `MenuSession.java`

This expands session state enough to support back navigation and current item context.

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

# 2. Update `ShopMenuRouter.java`

This now tracks and updates root/browse/detail sessions cleanly.

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

# 3. Update `ExchangeRootMenu.java`

Use the router rather than directly opening browse menus.

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

# 4. Update `ExchangeBrowseMenu.java`

Adds back button and router-based detail navigation.

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

# 5. Update `ExchangeItemDetailMenu.java`

Adds back button through router.

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

# 6. Turnover implementation

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockTurnoverServiceImpl.java`

Replace the empty implementation with this version.

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

# 7. MySQL repositories

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

# 8. Real reload support

## File: `src/main/java/com/splatage/wild_economy/bootstrap/PluginBootstrap.java`

Replace with this version that exposes reload.

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

# 10. Update `ShopMenuListener.java`

Add session cleanup and back-aware browse handling.

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
            final var session = this.shopMenuRouter.getSession(event.getWhoClicked().getUniqueId());
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
            final var session = this.shopMenuRouter.getSession(event.getWhoClicked().getUniqueId());
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

# 11. Commit 4 acceptance criteria

After applying these changes:

## GUI/session behavior

* players can go from root → browse → detail
* detail back button returns to the correct browse category/page
* browse back returns to root
* closing menus clears session state

## turnover

* scheduled stock turnover actually runs
* only `PLAYER_STOCKED` items are turned over
* turnover clamps at zero
* turnover writes log rows

## MySQL

* startup can use MySQL backend instead of SQLite
* schema version repo works on MySQL
* stock repo works on MySQL
* transaction repo works on MySQL

## reload

* `/shopadmin reload` actually rebuilds runtime services via bootstrap reload
* scheduled tasks are cancelled and re-registered cleanly

---

# 12. Known remaining limitations after Commit 4

Still acceptable to defer:

* async database execution in click paths
* more polished menu pagination and page buttons
* localized/menu-config-driven text everywhere
* admin stock adjustment commands with real implementations
* partial-stack sell-to-cap behavior
* more advanced inventory capacity checks for buying

---

# 13. Best next step after Commit 4

After this commit, the plugin should be a solid v1 base.

The best next direction is either:

* **v1 polish/testing pass** with bug fixes and UX cleanup, or
* **feature completion pass** for:

  * real admin stock tools
  * better buy quantity selection
  * config/message cleanup
  * performance optimization of DB paths

That is the point where implementation can shift from architecture build-out to refinement.
