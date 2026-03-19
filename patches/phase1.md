# wild_economy — Starter Repo File Plan

## Status

This document translates the current v1 technical spec into a **repo-starting file plan**.

It is intended to answer:

* what files should exist first
* where they should live
* what each file is responsible for
* what the first class/config stubs should look like

This is still a **planning artifact**, not a complete implementation.

---

# 1. Assumed repository layout

This assumes a standard Gradle Paper/Spigot plugin layout.

```text
wild_economy/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── splatage/
│   │   │           └── wild_economy/
│   │   │               ├── WildEconomyPlugin.java
│   │   │               ├── bootstrap/
│   │   │               │   ├── PluginBootstrap.java
│   │   │               │   └── ServiceRegistry.java
│   │   │               ├── command/
│   │   │               │   ├── ShopCommand.java
│   │   │               │   ├── ShopSellHandSubcommand.java
│   │   │               │   ├── ShopSellAllSubcommand.java
│   │   │               │   ├── ShopOpenSubcommand.java
│   │   │               │   ├── ShopAdminCommand.java
│   │   │               │   ├── ShopAdminReloadSubcommand.java
│   │   │               │   ├── ShopAdminStockSubcommand.java
│   │   │               │   ├── ShopAdminTurnoverSubcommand.java
│   │   │               │   └── ShopAdminQuoteSubcommand.java
│   │   │               ├── config/
│   │   │               │   ├── GlobalConfig.java
│   │   │               │   ├── DatabaseConfig.java
│   │   │               │   ├── ExchangeItemsConfig.java
│   │   │               │   ├── WorthImportConfig.java
│   │   │               │   ├── MessagesConfig.java
│   │   │               │   ├── ConfigLoader.java
│   │   │               │   └── ConfigValidator.java
│   │   │               ├── economy/
│   │   │               │   ├── EconomyGateway.java
│   │   │               │   ├── VaultEconomyGateway.java
│   │   │               │   └── EconomyResult.java
│   │   │               ├── exchange/
│   │   │               │   ├── catalog/
│   │   │               │   │   ├── ExchangeCatalog.java
│   │   │               │   │   ├── ExchangeCatalogEntry.java
│   │   │               │   │   ├── CatalogLoader.java
│   │   │               │   │   ├── WorthImporter.java
│   │   │               │   │   └── CatalogMergeService.java
│   │   │               │   ├── domain/
│   │   │               │   │   ├── ItemKey.java
│   │   │               │   │   ├── ItemPolicyMode.java
│   │   │               │   │   ├── ItemCategory.java
│   │   │               │   │   ├── StockState.java
│   │   │               │   │   ├── SellPriceBand.java
│   │   │               │   │   ├── StockSnapshot.java
│   │   │               │   │   ├── BuyQuote.java
│   │   │               │   │   ├── SellQuote.java
│   │   │               │   │   ├── SellLineResult.java
│   │   │               │   │   ├── SellHandResult.java
│   │   │               │   │   ├── SellAllResult.java
│   │   │               │   │   ├── BuyResult.java
│   │   │               │   │   ├── RejectionReason.java
│   │   │               │   │   └── TransactionType.java
│   │   │               │   ├── item/
│   │   │               │   │   ├── ItemNormalizer.java
│   │   │               │   │   ├── BukkitItemNormalizer.java
│   │   │               │   │   ├── ItemValidationService.java
│   │   │               │   │   ├── ItemValidationServiceImpl.java
│   │   │               │   │   ├── ValidationResult.java
│   │   │               │   │   └── CanonicalItemRules.java
│   │   │               │   ├── pricing/
│   │   │               │   │   ├── PricingService.java
│   │   │               │   │   ├── PricingServiceImpl.java
│   │   │               │   │   ├── SellPriceCurve.java
│   │   │               │   │   └── PriceFormatter.java
│   │   │               │   ├── stock/
│   │   │               │   │   ├── StockService.java
│   │   │               │   │   ├── StockServiceImpl.java
│   │   │               │   │   ├── StockTurnoverService.java
│   │   │               │   │   ├── StockTurnoverServiceImpl.java
│   │   │               │   │   └── StockStateResolver.java
│   │   │               │   ├── service/
│   │   │               │   │   ├── ExchangeService.java
│   │   │               │   │   ├── ExchangeServiceImpl.java
│   │   │               │   │   ├── ExchangeBrowseService.java
│   │   │               │   │   ├── ExchangeBuyService.java
│   │   │               │   │   ├── ExchangeBuyServiceImpl.java
│   │   │               │   │   ├── ExchangeSellService.java
│   │   │               │   │   ├── ExchangeSellServiceImpl.java
│   │   │               │   │   ├── TransactionLogService.java
│   │   │               │   │   └── TransactionLogServiceImpl.java
│   │   │               │   └── repository/
│   │   │               │       ├── ExchangeStockRepository.java
│   │   │               │       ├── ExchangeTransactionRepository.java
│   │   │               │       ├── SchemaVersionRepository.java
│   │   │               │       ├── sqlite/
│   │   │               │       │   ├── SqliteExchangeStockRepository.java
│   │   │               │       │   ├── SqliteExchangeTransactionRepository.java
│   │   │               │       │   └── SqliteSchemaVersionRepository.java
│   │   │               │       └── mysql/
│   │   │               │           ├── MysqlExchangeStockRepository.java
│   │   │               │           ├── MysqlExchangeTransactionRepository.java
│   │   │               │           └── MysqlSchemaVersionRepository.java
│   │   │               ├── gui/
│   │   │               │   ├── ShopMenuRouter.java
│   │   │               │   ├── MenuSession.java
│   │   │               │   ├── ExchangeRootMenu.java
│   │   │               │   ├── ExchangeBrowseMenu.java
│   │   │               │   ├── ExchangeItemDetailMenu.java
│   │   │               │   └── MenuText.java
│   │   │               ├── persistence/
│   │   │               │   ├── DatabaseProvider.java
│   │   │               │   ├── DatabaseDialect.java
│   │   │               │   ├── ConnectionFactory.java
│   │   │               │   ├── MigrationManager.java
│   │   │               │   ├── TransactionRunner.java
│   │   │               │   └── JdbcUtils.java
│   │   │               ├── scheduler/
│   │   │               │   ├── AsyncExecutor.java
│   │   │               │   └── StockTurnoverTask.java
│   │   │               └── util/
│   │   │                   ├── InventorySnapshot.java
│   │   │                   ├── MoneyFormatter.java
│   │   │                   ├── Preconditions.java
│   │   │                   └── TimeProvider.java
│   │   └── resources/
│   │       ├── plugin.yml
│   │       ├── config.yml
│   │       ├── database.yml
│   │       ├── exchange-items.yml
│   │       ├── worth-import.yml
│   │       ├── messages.yml
│   │       └── db/
│   │           └── migration/
│   │               ├── sqlite/
│   │               │   └── V1__initial_schema.sql
│   │               └── mysql/
│   │                   └── V1__initial_schema.sql
│   └── test/
│       └── java/
│           └── com/
│               └── splatage/
│                   └── wild_economy/
│                       └── placeholder/
│                           └── PlaceholderTest.java
```

---

# 2. Recommended creation order

## Phase 1 — bare plugin start

Create first:

1. `src/main/resources/plugin.yml`
2. `src/main/java/com/splatage/wild_economy/WildEconomyPlugin.java`
3. `src/main/java/com/splatage/wild_economy/bootstrap/PluginBootstrap.java`
4. `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`
5. `src/main/resources/config.yml`
6. `src/main/resources/database.yml`
7. `src/main/resources/exchange-items.yml`
8. `src/main/resources/worth-import.yml`
9. `src/main/resources/messages.yml`

## Phase 2 — core domain and config loading

10. `exchange/domain/*`
11. `config/*`
12. `exchange/catalog/*`
13. `economy/*`

## Phase 3 — persistence foundation

14. `persistence/*`
15. `exchange/repository/*`
16. SQL migrations

## Phase 4 — sell path first

17. `exchange/item/*`
18. `exchange/pricing/*`
19. `exchange/stock/*`
20. `exchange/service/*`
21. `command/ShopCommand.java`
22. `command/ShopSellHandSubcommand.java`
23. `command/ShopSellAllSubcommand.java`

## Phase 5 — buy GUI

24. `gui/*`
25. `command/ShopOpenSubcommand.java`
26. `exchange/service/ExchangeBrowseService.java`
27. `exchange/service/ExchangeBuyService*.java`

## Phase 6 — admin and turnover

28. `scheduler/*`
29. `command/ShopAdmin*.java`

---

# 3. Exact resource files

## File: `src/main/resources/plugin.yml`

```yaml
name: wild_economy
version: 0.1.0
main: com.splatage.wild_economy.WildEconomyPlugin
api-version: '1.21'
author: splatage
description: Curated Exchange-first economy plugin for The Wild.
depend:
  - Vault
softdepend:
  - Essentials
commands:
  shop:
    description: Open the shop or use sell commands.
    usage: /shop [sellhand|sellall]
    aliases: [exchange]
    permission: wild_economy.shop
  shopadmin:
    description: Admin commands for wild_economy.
    usage: /shopadmin <reload|stock|turnover|quote>
    permission: wild_economy.admin
permissions:
  wild_economy.shop:
    description: Allows use of player shop commands.
    default: true
  wild_economy.shop.sell:
    description: Allows selling to the Exchange.
    default: true
  wild_economy.shop.buy:
    description: Allows buying from the Exchange.
    default: true
  wild_economy.admin:
    description: Allows administrative use of wild_economy.
    default: op
```

## File: `src/main/resources/config.yml`

```yaml
turnover:
  interval-ticks: 72000

gui:
  page-size: 45
  title-root: "Shop"
  title-browse-prefix: "Shop - "
  title-detail-prefix: "Buy - "

commands:
  base-command: shop
  admin-command: shopadmin

logging:
  debug: false
```

## File: `src/main/resources/database.yml`

```yaml
backend: sqlite

sqlite:
  file: plugins/wild_economy/data.db

mysql:
  host: 127.0.0.1
  port: 3306
  database: wild_economy
  username: root
  password: change-me
  ssl: false
  maximum-pool-size: 10
```

## File: `src/main/resources/worth-import.yml`

```yaml
enabled: true
essentials-worth-file: plugins/Essentials/worth.yml

import:
  use-worth-as-base-value: true
  explicit-item-config-overrides-worth: true
  ignore-missing-worth: true
```

## File: `src/main/resources/messages.yml`

```yaml
shop:
  opened: "&aOpened shop."
  sellhand-none: "&cYou are not holding a sellable item."
  sellall-none: "&cYou have no sellable items in your inventory."
  buy-failed-out-of-stock: "&cThat item is out of stock."
  buy-failed-funds: "&cYou do not have enough money."
  internal-error: "&cAn internal error occurred."

sell:
  hand-success: "&aSold {amount}x {item} for {money}."
  all-success: "&aSold items for a total of {money}."
  tapered-note: "&eSome items sold at reduced value because stock is high."

admin:
  reload-success: "&aReloaded wild_economy."
  stock-set: "&aSet stock for {item} to {amount}."
  turnover-run: "&aRan stock turnover pass."
```

## File: `src/main/resources/exchange-items.yml`

```yaml
items:
  minecraft:wheat:
    display-name: Wheat
    category: FARMING
    policy: PLAYER_STOCKED
    buy-enabled: true
    sell-enabled: true
    stock-cap: 10000
    turnover-amount-per-interval: 250
    buy-price: 12.0
    sell-price: 8.0
    sell-price-bands:
      - min-fill: 0.00
        max-fill: 0.25
        multiplier: 1.00
      - min-fill: 0.25
        max-fill: 0.50
        multiplier: 0.85
      - min-fill: 0.50
        max-fill: 0.75
        multiplier: 0.65
      - min-fill: 0.75
        max-fill: 0.90
        multiplier: 0.40
      - min-fill: 0.90
        max-fill: 1.01
        multiplier: 0.20

  minecraft:cactus:
    display-name: Cactus
    category: FARMING
    policy: PLAYER_STOCKED
    buy-enabled: true
    sell-enabled: true
    stock-cap: 12000
    turnover-amount-per-interval: 400
    buy-price: 10.0
    sell-price: 6.0
    sell-price-bands:
      - min-fill: 0.00
        max-fill: 0.25
        multiplier: 1.00
      - min-fill: 0.25
        max-fill: 0.50
        multiplier: 0.85
      - min-fill: 0.50
        max-fill: 0.75
        multiplier: 0.65
      - min-fill: 0.75
        max-fill: 0.90
        multiplier: 0.40
      - min-fill: 0.90
        max-fill: 1.01
        multiplier: 0.20

  minecraft:sand:
    display-name: Sand
    category: BUILDING
    policy: UNLIMITED_BUY
    buy-enabled: true
    sell-enabled: false
    stock-cap: 0
    turnover-amount-per-interval: 0
    buy-price: 24.0
    sell-price: 0.0
    sell-price-bands: []

  minecraft:diamond:
    display-name: Diamond
    category: MINING
    policy: DISABLED
    buy-enabled: false
    sell-enabled: false
    stock-cap: 0
    turnover-amount-per-interval: 0
    buy-price: 0.0
    sell-price: 0.0
    sell-price-bands: []
```

---

# 4. Exact migration files

## File: `src/main/resources/db/migration/sqlite/V1__initial_schema.sql`

```sql
CREATE TABLE IF NOT EXISTS exchange_stock (
    item_key TEXT PRIMARY KEY,
    stock_count INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS exchange_transactions (
    transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_type TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    item_key TEXT NOT NULL,
    amount INTEGER NOT NULL,
    unit_price NUMERIC NOT NULL,
    total_value NUMERIC NOT NULL,
    created_at INTEGER NOT NULL,
    meta_json TEXT NULL
);

CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER NOT NULL PRIMARY KEY,
    applied_at INTEGER NOT NULL
);
```

## File: `src/main/resources/db/migration/mysql/V1__initial_schema.sql`

```sql
CREATE TABLE IF NOT EXISTS exchange_stock (
    item_key VARCHAR(128) PRIMARY KEY,
    stock_count BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS exchange_transactions (
    transaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_type VARCHAR(32) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    item_key VARCHAR(128) NOT NULL,
    amount INT NOT NULL,
    unit_price DECIMAL(18,4) NOT NULL,
    total_value DECIMAL(18,4) NOT NULL,
    created_at BIGINT NOT NULL,
    meta_json TEXT NULL
);

CREATE TABLE IF NOT EXISTS schema_version (
    version INT NOT NULL PRIMARY KEY,
    applied_at BIGINT NOT NULL
);
```

---

# 5. First class stubs

These are the minimum stubs I would create first so the repo has a clean backbone.

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
        this.bootstrap = new PluginBootstrap(this);
        this.bootstrap.enable();
    }

    @Override
    public void onDisable() {
        if (this.bootstrap != null) {
            this.bootstrap.disable();
        }
    }
}
```

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

    public void disable() {
        if (this.services != null) {
            this.services.shutdown();
        }
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

```java
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Load configs
        // Initialize database provider
        // Run migrations
        // Build catalog
        // Create services
    }

    public void registerCommands() {
        // Register /shop and /shopadmin executors
    }

    public void registerTasks() {
        // Register turnover scheduler
    }

    public void shutdown() {
        // Close pools/executors if needed
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/ItemKey.java`

```java
package com.splatage.wild_economy.exchange.domain;

public record ItemKey(String value) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/ItemPolicyMode.java`

```java
package com.splatage.wild_economy.exchange.domain;

public enum ItemPolicyMode {
    PLAYER_STOCKED,
    UNLIMITED_BUY,
    DISABLED
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/ItemCategory.java`

```java
package com.splatage.wild_economy.exchange.domain;

public enum ItemCategory {
    FARMING,
    MINING,
    MOB_DROPS,
    BUILDING,
    UTILITY
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/StockState.java`

```java
package com.splatage.wild_economy.exchange.domain;

public enum StockState {
    OUT_OF_STOCK,
    LOW,
    HEALTHY,
    HIGH,
    SATURATED
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/SellPriceBand.java`

```java
package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record SellPriceBand(
    double minFillRatioInclusive,
    double maxFillRatioExclusive,
    BigDecimal multiplier
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/ExchangeCatalogEntry.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import java.math.BigDecimal;
import java.util.List;

public record ExchangeCatalogEntry(
    ItemKey itemKey,
    String displayName,
    ItemCategory category,
    ItemPolicyMode policyMode,
    BigDecimal baseWorth,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    long stockCap,
    long turnoverAmountPerInterval,
    List<SellPriceBand> sellPriceBands,
    boolean buyEnabled,
    boolean sellEnabled
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/ExchangeCatalog.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ExchangeCatalog {

    private final Map<ItemKey, ExchangeCatalogEntry> entries;

    public ExchangeCatalog(final Map<ItemKey, ExchangeCatalogEntry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public Optional<ExchangeCatalogEntry> get(final ItemKey itemKey) {
        return Optional.ofNullable(this.entries.get(itemKey));
    }

    public Collection<ExchangeCatalogEntry> allEntries() {
        return this.entries.values();
    }

    public List<ExchangeCatalogEntry> byCategory(final ItemCategory category) {
        return this.entries.values().stream()
            .filter(entry -> entry.category() == category)
            .collect(Collectors.toList());
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/economy/EconomyGateway.java`

```java
package com.splatage.wild_economy.economy;

import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyGateway {
    EconomyResult deposit(UUID playerId, BigDecimal amount);
    EconomyResult withdraw(UUID playerId, BigDecimal amount);
    BigDecimal getBalance(UUID playerId);
}
```

## File: `src/main/java/com/splatage/wild_economy/economy/EconomyResult.java`

```java
package com.splatage.wild_economy.economy;

public record EconomyResult(boolean success, String message) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/ExchangeStockRepository.java`

```java
package com.splatage.wild_economy.exchange.repository;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Map;

public interface ExchangeStockRepository {
    long getStock(ItemKey itemKey);
    Map<ItemKey, Long> getStocks(Iterable<ItemKey> itemKeys);
    void incrementStock(ItemKey itemKey, int amount);
    void decrementStock(ItemKey itemKey, int amount);
    void setStock(ItemKey itemKey, long stock);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/ExchangeTransactionRepository.java`

```java
package com.splatage.wild_economy.exchange.repository;

import com.splatage.wild_economy.exchange.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ExchangeTransactionRepository {
    void insert(
        TransactionType type,
        UUID playerId,
        String itemKey,
        int amount,
        BigDecimal unitPrice,
        BigDecimal totalValue,
        Instant createdAt,
        String metaJson
    );
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeService.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.List;
import java.util.UUID;

public interface ExchangeService {
    SellHandResult sellHand(UUID playerId);
    SellAllResult sellAll(UUID playerId);
    BuyResult buy(UUID playerId, ItemKey itemKey, int amount);

    List<ExchangeCatalogView> browseCategory(ItemCategory category, int page, int pageSize);
    ExchangeItemView getItemView(ItemKey itemKey);
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`

```java
package com.splatage.wild_economy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        // Route to open, sellhand, sellall
        return true;
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeRootMenu.java`

```java
package com.splatage.wild_economy.gui;

import org.bukkit.entity.Player;

public final class ExchangeRootMenu {

    public void open(final Player player) {
        // Build and open root category menu
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/scheduler/StockTurnoverTask.java`

```java
package com.splatage.wild_economy.scheduler;

import com.splatage.wild_economy.exchange.stock.StockTurnoverService;

public final class StockTurnoverTask implements Runnable {

    private final StockTurnoverService turnoverService;

    public StockTurnoverTask(final StockTurnoverService turnoverService) {
        this.turnoverService = turnoverService;
    }

    @Override
    public void run() {
        this.turnoverService.runTurnoverPass();
    }
}
```

---

# 6. Minimal view models to stub early

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeCatalogView.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockState;
import java.math.BigDecimal;

public record ExchangeCatalogView(
    ItemKey itemKey,
    String displayName,
    BigDecimal buyPrice,
    long stockCount,
    StockState stockState
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeItemView.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockState;
import java.math.BigDecimal;

public record ExchangeItemView(
    ItemKey itemKey,
    String displayName,
    BigDecimal buyPrice,
    long stockCount,
    long stockCap,
    StockState stockState,
    boolean buyEnabled
) {}
```

---

# 7. Minimal starter README section

## File: `README.md`

Suggested starter content:

```md
# wild_economy

A curated, Exchange-first Minecraft economy plugin for The Wild.

## v1 direction
- GUI-driven buying
- Command-driven selling
- Player-stocked Exchange as the default
- Unlimited buy-only only for selected nuisance/world-damaging materials
- Progression-sensitive items disabled
- Stock caps, background turnover, and stock-sensitive sell taper

## Commands
- `/shop`
- `/shop sellhand`
- `/shop sellall`
- `/shopadmin ...`

## Status
Implementation scaffold / v1 foundation.
```

---

# 8. First commit recommendation

The cleanest first repo commit would include:

* `plugin.yml`
* all config resource skeletons
* migration SQL files
* `WildEconomyPlugin.java`
* `PluginBootstrap.java`
* `ServiceRegistry.java`
* core domain records/enums
* repository interfaces
* service interfaces
* empty command/gui/task stubs
* README

That gives you a coherent project skeleton without pretending core logic already exists.

---

# 9. Second commit recommendation

The second commit should focus on the **sell path first**:

* config loading
* catalog load/build
* Vault gateway
* stock repository implementation
* transaction repository implementation
* item normalization
* `/shop sellhand`
* `/shop sellall`

This is the highest-value first functional slice.

---

# 10. Hard guardrails for the starter repo

* Do not put pricing logic in commands
* Do not put SQL in service classes
* Do not put business logic in GUI classes
* Do not add fuzzy item matching
* Do not make buy prices dynamic in v1
* Do not add Marketplace scaffolding unless scope changes
* Keep early stubs thin and boring

The starter repo should communicate structure clearly, not fake completeness.
