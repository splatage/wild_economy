# wild_economy — Initial File Set (First Commit)

## Status

This document contains the **copy-ready initial file set** for the **first repo commit**.

This is intentionally a **skeleton commit**, not a fake-complete implementation.
It is designed to establish:

* package structure
* plugin bootstrap
* command surface
* config/resource layout
* core domain types
* repository/service interfaces
* migration placeholders

It does **not** attempt to implement the full Exchange logic yet.

---

# 1. Root files

## File: `settings.gradle.kts`

```kotlin
rootProject.name = "wild_economy"
```

## File: `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=true
org.gradle.parallel=true
```

## File: `build.gradle.kts`

```kotlin
plugins {
    java
}

group = "com.splatage"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(
            mapOf(
                "version" to project.version
            )
        )
    }
}
```

## File: `README.md`

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
Initial scaffold / v1 foundation.
```

---

# 2. Resource files

## File: `src/main/resources/plugin.yml`

```yaml
name: wild_economy
version: ${version}
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

# 3. Migration files

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

# 4. Java source files

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
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import org.bukkit.command.PluginCommand;

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
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand());
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand());
        }
    }

    public void registerTasks() {
        // Register turnover scheduler
    }

    public void shutdown() {
        // Close pools/executors if needed
    }
}
```

---

# 5. Command stubs

## File: `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`

```java
package com.splatage.wild_economy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        // Route to open, sellhand, sellall.
        return true;
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopAdminCommand.java`

```java
package com.splatage.wild_economy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopAdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        // Route to reload, stock, turnover, quote.
        return true;
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellHandSubcommand.java`

```java
package com.splatage.wild_economy.command;

public final class ShopSellHandSubcommand {
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellAllSubcommand.java`

```java
package com.splatage.wild_economy.command;

public final class ShopSellAllSubcommand {
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopOpenSubcommand.java`

```java
package com.splatage.wild_economy.command;

public final class ShopOpenSubcommand {
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopAdminReloadSubcommand.java`

```java
package com.splatage.wild_economy.command;

public final class ShopAdminReloadSubcommand {
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopAdminStockSubcommand.java`

```java
package com.splatage.wild_economy.command;

public final class ShopAdminStockSubcommand {
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopAdminTurnoverSubcommand.java`

```java
package com.splatage.wild_economy.command;

public final class ShopAdminTurnoverSubcommand {
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopAdminQuoteSubcommand.java`

```java
package com.splatage.wild_economy.command;

public final class ShopAdminQuoteSubcommand {
}
```

---

# 6. Config stubs

## File: `src/main/java/com/splatage/wild_economy/config/GlobalConfig.java`

```java
package com.splatage.wild_economy.config;

public record GlobalConfig(
    long turnoverIntervalTicks,
    int guiPageSize,
    String baseCommand,
    String adminCommand,
    boolean debugLogging
) {}
```

## File: `src/main/java/com/splatage/wild_economy/config/DatabaseConfig.java`

```java
package com.splatage.wild_economy.config;

public record DatabaseConfig(
    String backend,
    String sqliteFile,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    boolean mysqlSsl,
    int mysqlMaximumPoolSize
) {}
```

## File: `src/main/java/com/splatage/wild_economy/config/WorthImportConfig.java`

```java
package com.splatage.wild_economy.config;

public record WorthImportConfig(
    boolean enabled,
    String essentialsWorthFile,
    boolean useWorthAsBaseValue,
    boolean explicitItemConfigOverridesWorth,
    boolean ignoreMissingWorth
) {}
```

## File: `src/main/java/com/splatage/wild_economy/config/ExchangeItemsConfig.java`

```java
package com.splatage.wild_economy.config;

public final class ExchangeItemsConfig {
}
```

## File: `src/main/java/com/splatage/wild_economy/config/MessagesConfig.java`

```java
package com.splatage.wild_economy.config;

public final class MessagesConfig {
}
```

## File: `src/main/java/com/splatage/wild_economy/config/ConfigLoader.java`

```java
package com.splatage.wild_economy.config;

import com.splatage.wild_economy.WildEconomyPlugin;

public final class ConfigLoader {

    private final WildEconomyPlugin plugin;

    public ConfigLoader(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public GlobalConfig loadGlobalConfig() {
        return new GlobalConfig(72000L, 45, "shop", "shopadmin", false);
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/config/ConfigValidator.java`

```java
package com.splatage.wild_economy.config;

public final class ConfigValidator {

    public void validate() {
        // Validate config values in later implementation.
    }
}
```

---

# 7. Domain files

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

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/StockSnapshot.java`

```java
package com.splatage.wild_economy.exchange.domain;

public record StockSnapshot(
    ItemKey itemKey,
    long stockCount,
    long stockCap,
    double fillRatio,
    StockState stockState
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/BuyQuote.java`

```java
package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record BuyQuote(
    ItemKey itemKey,
    int amount,
    BigDecimal unitPrice,
    BigDecimal totalPrice
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/SellQuote.java`

```java
package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record SellQuote(
    ItemKey itemKey,
    int amount,
    BigDecimal baseUnitPrice,
    BigDecimal effectiveUnitPrice,
    BigDecimal totalPrice,
    double stockFillRatio,
    boolean tapered
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/SellLineResult.java`

```java
package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record SellLineResult(
    ItemKey itemKey,
    String displayName,
    int amountSold,
    BigDecimal effectiveUnitPrice,
    BigDecimal totalEarned,
    boolean tapered
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/SellHandResult.java`

```java
package com.splatage.wild_economy.exchange.domain;

public record SellHandResult(
    boolean success,
    SellLineResult lineResult,
    RejectionReason rejectionReason,
    String message
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/SellAllResult.java`

```java
package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;
import java.util.List;

public record SellAllResult(
    boolean success,
    List<SellLineResult> soldLines,
    BigDecimal totalEarned,
    List<String> skippedDescriptions,
    String message
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/BuyResult.java`

```java
package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record BuyResult(
    boolean success,
    ItemKey itemKey,
    int amountBought,
    BigDecimal totalCost,
    String message
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/RejectionReason.java`

```java
package com.splatage.wild_economy.exchange.domain;

public enum RejectionReason {
    ITEM_NOT_ELIGIBLE,
    ITEM_DISABLED,
    SELL_NOT_ALLOWED,
    BUY_NOT_ALLOWED,
    STOCK_FULL,
    OUT_OF_STOCK,
    INSUFFICIENT_FUNDS,
    INVALID_ITEM_STATE,
    INVENTORY_FULL,
    INTERNAL_ERROR
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/TransactionType.java`

```java
package com.splatage.wild_economy.exchange.domain;

public enum TransactionType {
    SELL,
    BUY,
    TURNOVER
}
```

---

# 8. Catalog files

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

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogLoader.java`

```java
package com.splatage.wild_economy.exchange.catalog;

public final class CatalogLoader {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/WorthImporter.java`

```java
package com.splatage.wild_economy.exchange.catalog;

public final class WorthImporter {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogMergeService.java`

```java
package com.splatage.wild_economy.exchange.catalog;

public final class CatalogMergeService {
}
```

---

# 9. Economy files

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

## File: `src/main/java/com/splatage/wild_economy/economy/VaultEconomyGateway.java`

```java
package com.splatage.wild_economy.economy;

import java.math.BigDecimal;
import java.util.UUID;

public final class VaultEconomyGateway implements EconomyGateway {

    @Override
    public EconomyResult deposit(final UUID playerId, final BigDecimal amount) {
        return new EconomyResult(false, "Not implemented");
    }

    @Override
    public EconomyResult withdraw(final UUID playerId, final BigDecimal amount) {
        return new EconomyResult(false, "Not implemented");
    }

    @Override
    public BigDecimal getBalance(final UUID playerId) {
        return BigDecimal.ZERO;
    }
}
```

---

# 10. Item validation files

## File: `src/main/java/com/splatage/wild_economy/exchange/item/ItemNormalizer.java`

```java
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public interface ItemNormalizer {
    Optional<ItemKey> normalizeForExchange(ItemStack itemStack);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/item/BukkitItemNormalizer.java`

```java
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class BukkitItemNormalizer implements ItemNormalizer {

    @Override
    public Optional<ItemKey> normalizeForExchange(final ItemStack itemStack) {
        return Optional.empty();
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/item/ItemValidationService.java`

```java
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import org.bukkit.inventory.ItemStack;

public interface ItemValidationService {
    ValidationResult validateForSell(ItemStack itemStack);
    ValidationResult validateForBuy(ItemKey itemKey);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/item/ValidationResult.java`

```java
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;

public record ValidationResult(
    boolean valid,
    ItemKey itemKey,
    RejectionReason rejectionReason,
    String detail
) {}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/item/ItemValidationServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import org.bukkit.inventory.ItemStack;

public final class ItemValidationServiceImpl implements ItemValidationService {

    @Override
    public ValidationResult validateForSell(final ItemStack itemStack) {
        return new ValidationResult(false, null, RejectionReason.INTERNAL_ERROR, "Not implemented");
    }

    @Override
    public ValidationResult validateForBuy(final ItemKey itemKey) {
        return new ValidationResult(false, itemKey, RejectionReason.INTERNAL_ERROR, "Not implemented");
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/item/CanonicalItemRules.java`

```java
package com.splatage.wild_economy.exchange.item;

public final class CanonicalItemRules {
}
```

---

# 11. Pricing files

## File: `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingService.java`

```java
package com.splatage.wild_economy.exchange.pricing;

import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public interface PricingService {
    BuyQuote quoteBuy(ItemKey itemKey, int amount, StockSnapshot stockSnapshot);
    SellQuote quoteSell(ItemKey itemKey, int amount, StockSnapshot stockSnapshot);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.pricing;

import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public final class PricingServiceImpl implements PricingService {

    @Override
    public BuyQuote quoteBuy(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SellQuote quoteSell(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/pricing/SellPriceCurve.java`

```java
package com.splatage.wild_economy.exchange.pricing;

public final class SellPriceCurve {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/pricing/PriceFormatter.java`

```java
package com.splatage.wild_economy.exchange.pricing;

public final class PriceFormatter {
}
```

---

# 12. Stock files

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockService.java`

```java
package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public interface StockService {
    StockSnapshot getSnapshot(ItemKey itemKey);
    long getAvailableRoom(ItemKey itemKey);
    void addStock(ItemKey itemKey, int amount);
    void removeStock(ItemKey itemKey, int amount);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public final class StockServiceImpl implements StockService {

    @Override
    public StockSnapshot getSnapshot(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public long getAvailableRoom(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockTurnoverService.java`

```java
package com.splatage.wild_economy.exchange.stock;

public interface StockTurnoverService {
    void runTurnoverPass();
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockTurnoverServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.stock;

public final class StockTurnoverServiceImpl implements StockTurnoverService {

    @Override
    public void runTurnoverPass() {
        // Not implemented yet.
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockStateResolver.java`

```java
package com.splatage.wild_economy.exchange.stock;

public final class StockStateResolver {
}
```

---

# 13. Service files

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

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.List;
import java.util.UUID;

public final class ExchangeServiceImpl implements ExchangeService {

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(final ItemCategory category, final int page, final int pageSize) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseService.java`

```java
package com.splatage.wild_economy.exchange.service;

public interface ExchangeBrowseService {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyService.java`

```java
package com.splatage.wild_economy.exchange.service;

public interface ExchangeBuyService {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

public final class ExchangeBuyServiceImpl implements ExchangeBuyService {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellService.java`

```java
package com.splatage.wild_economy.exchange.service;

public interface ExchangeSellService {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

public final class ExchangeSellServiceImpl implements ExchangeSellService {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/TransactionLogService.java`

```java
package com.splatage.wild_economy.exchange.service;

public interface TransactionLogService {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/TransactionLogServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

public final class TransactionLogServiceImpl implements TransactionLogService {
}
```

---

# 14. Repository files

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

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/SchemaVersionRepository.java`

```java
package com.splatage.wild_economy.exchange.repository;

public interface SchemaVersionRepository {
    int getCurrentVersion();
    void setCurrentVersion(int version);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/sqlite/SqliteExchangeStockRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import java.util.Map;

public final class SqliteExchangeStockRepository implements ExchangeStockRepository {

    @Override
    public long getStock(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Map<ItemKey, Long> getStocks(final Iterable<ItemKey> itemKeys) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void incrementStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void decrementStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setStock(final ItemKey itemKey, final long stock) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/sqlite/SqliteExchangeTransactionRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SqliteExchangeTransactionRepository implements ExchangeTransactionRepository {

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
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/sqlite/SqliteSchemaVersionRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;

public final class SqliteSchemaVersionRepository implements SchemaVersionRepository {

    @Override
    public int getCurrentVersion() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setCurrentVersion(final int version) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/mysql/MysqlExchangeStockRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.mysql;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import java.util.Map;

public final class MysqlExchangeStockRepository implements ExchangeStockRepository {

    @Override
    public long getStock(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Map<ItemKey, Long> getStocks(final Iterable<ItemKey> itemKeys) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void incrementStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void decrementStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setStock(final ItemKey itemKey, final long stock) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/mysql/MysqlExchangeTransactionRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.mysql;

import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MysqlExchangeTransactionRepository implements ExchangeTransactionRepository {

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
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/mysql/MysqlSchemaVersionRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.mysql;

import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;

public final class MysqlSchemaVersionRepository implements SchemaVersionRepository {

    @Override
    public int getCurrentVersion() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setCurrentVersion(final int version) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

---

# 15. GUI and scheduler stubs

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuRouter.java`

```java
package com.splatage.wild_economy.gui;

public final class ShopMenuRouter {
}
```

## File: `src/main/java/com/splatage/wild_economy/gui/MenuSession.java`

```java
package com.splatage.wild_economy.gui;

public final class MenuSession {
}
```

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeRootMenu.java`

```java
package com.splatage.wild_economy.gui;

import org.bukkit.entity.Player;

public final class ExchangeRootMenu {

    public void open(final Player player) {
        // Build and open root category menu.
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeBrowseMenu.java`

```java
package com.splatage.wild_economy.gui;

public final class ExchangeBrowseMenu {
}
```

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeItemDetailMenu.java`

```java
package com.splatage.wild_economy.gui;

public final class ExchangeItemDetailMenu {
}
```

## File: `src/main/java/com/splatage/wild_economy/gui/MenuText.java`

```java
package com.splatage.wild_economy.gui;

public final class MenuText {
}
```

## File: `src/main/java/com/splatage/wild_economy/scheduler/AsyncExecutor.java`

```java
package com.splatage.wild_economy.scheduler;

public final class AsyncExecutor {
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

# 16. Persistence and util stubs

## File: `src/main/java/com/splatage/wild_economy/persistence/DatabaseDialect.java`

```java
package com.splatage.wild_economy.persistence;

public enum DatabaseDialect {
    SQLITE,
    MYSQL
}
```

## File: `src/main/java/com/splatage/wild_economy/persistence/DatabaseProvider.java`

```java
package com.splatage.wild_economy.persistence;

public final class DatabaseProvider {
}
```

## File: `src/main/java/com/splatage/wild_economy/persistence/ConnectionFactory.java`

```java
package com.splatage.wild_economy.persistence;

public final class ConnectionFactory {
}
```

## File: `src/main/java/com/splatage/wild_economy/persistence/MigrationManager.java`

```java
package com.splatage.wild_economy.persistence;

public final class MigrationManager {
}
```

## File: `src/main/java/com/splatage/wild_economy/persistence/TransactionRunner.java`

```java
package com.splatage.wild_economy.persistence;

public final class TransactionRunner {
}
```

## File: `src/main/java/com/splatage/wild_economy/persistence/JdbcUtils.java`

```java
package com.splatage.wild_economy.persistence;

public final class JdbcUtils {
}
```

## File: `src/main/java/com/splatage/wild_economy/util/InventorySnapshot.java`

```java
package com.splatage.wild_economy.util;

public final class InventorySnapshot {
}
```

## File: `src/main/java/com/splatage/wild_economy/util/MoneyFormatter.java`

```java
package com.splatage.wild_economy.util;

public final class MoneyFormatter {
}
```

## File: `src/main/java/com/splatage/wild_economy/util/Preconditions.java`

```java
package com.splatage.wild_economy.util;

public final class Preconditions {
}
```

## File: `src/main/java/com/splatage/wild_economy/util/TimeProvider.java`

```java
package com.splatage.wild_economy.util;

public final class TimeProvider {
}
```

---

# 17. First commit contents summary

The first commit should include exactly this kind of scaffold:

* build files
* resource/config files
* migration files
* plugin bootstrap
* command stubs
* core domain records/enums
* catalog shell
* repository interfaces + empty backend implementations
* service interfaces + empty stubs
* GUI shells
* scheduler shell
* persistence shells
* utility shells

This is enough to establish the repo cleanly without pretending the Exchange works yet.

---

# 18. Recommended second commit

The second commit should make the first real vertical slice work:

* load configs
* load worth import and item catalog
* implement item normalization
* implement stock repository for SQLite first
* implement transaction repository for SQLite first
* implement Vault economy bridge
* implement `/shop sellhand`
* implement `/shop sellall`

That is the best first functional slice because it exercises:

* item policy
* pricing
* stock buffering
* economy payout
* transaction logging

before UI complexity is added.
