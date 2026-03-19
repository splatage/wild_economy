# wild_economy — Commit 2A Copy-Ready Files

## Status

This document contains **copy-ready contents** for **Commit 2A** only.

Commit 2A scope:

* config loading
* worth import
* catalog build
* strict item normalization
* strict item validation

This artifact is intended to flesh out these exact files:

* `ConfigLoader.java`
* `ExchangeItemsConfig.java`
* `WorthImporter.java`
* `CatalogLoader.java`
* `CatalogMergeService.java`
* `BukkitItemNormalizer.java`
* `ItemValidationServiceImpl.java`
* `CanonicalItemRules.java`

These contents assume the first-commit scaffold already exists.

---

## File: `src/main/java/com/splatage/wild_economy/config/ExchangeItemsConfig.java`

```java
package com.splatage.wild_economy.config;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExchangeItemsConfig {

    private final Map<ItemKey, RawItemEntry> items;

    public ExchangeItemsConfig(final Map<ItemKey, RawItemEntry> items) {
        this.items = Map.copyOf(items);
    }

    public Map<ItemKey, RawItemEntry> items() {
        return this.items;
    }

    public Optional<RawItemEntry> get(final ItemKey itemKey) {
        return Optional.ofNullable(this.items.get(itemKey));
    }

    public record RawItemEntry(
        ItemKey itemKey,
        String displayName,
        ItemCategory category,
        ItemPolicyMode policyMode,
        boolean buyEnabled,
        boolean sellEnabled,
        long stockCap,
        long turnoverAmountPerInterval,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        List<SellPriceBand> sellPriceBands
    ) {
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/config/ConfigLoader.java`

```java
package com.splatage.wild_economy.config;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigLoader {

    private final WildEconomyPlugin plugin;

    public ConfigLoader(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public GlobalConfig loadGlobalConfig() {
        final FileConfiguration config = this.plugin.getConfig();
        return new GlobalConfig(
            config.getLong("turnover.interval-ticks", 72000L),
            config.getInt("gui.page-size", 45),
            config.getString("commands.base-command", "shop"),
            config.getString("commands.admin-command", "shopadmin"),
            config.getBoolean("logging.debug", false)
        );
    }

    public DatabaseConfig loadDatabaseConfig() {
        final FileConfiguration config = this.loadYaml("database.yml");
        return new DatabaseConfig(
            config.getString("backend", "sqlite"),
            config.getString("sqlite.file", "plugins/wild_economy/data.db"),
            config.getString("mysql.host", "127.0.0.1"),
            config.getInt("mysql.port", 3306),
            config.getString("mysql.database", "wild_economy"),
            config.getString("mysql.username", "root"),
            config.getString("mysql.password", "change-me"),
            config.getBoolean("mysql.ssl", false),
            config.getInt("mysql.maximum-pool-size", 10)
        );
    }

    public WorthImportConfig loadWorthImportConfig() {
        final FileConfiguration config = this.loadYaml("worth-import.yml");
        return new WorthImportConfig(
            config.getBoolean("enabled", true),
            config.getString("essentials-worth-file", "plugins/Essentials/worth.yml"),
            config.getBoolean("import.use-worth-as-base-value", true),
            config.getBoolean("import.explicit-item-config-overrides-worth", true),
            config.getBoolean("import.ignore-missing-worth", true)
        );
    }

    public ExchangeItemsConfig loadExchangeItemsConfig() {
        final FileConfiguration config = this.loadYaml("exchange-items.yml");
        final ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            throw new IllegalStateException("exchange-items.yml is missing the 'items' section");
        }

        final Map<ItemKey, ExchangeItemsConfig.RawItemEntry> items = new LinkedHashMap<>();
        for (final String rawItemKey : itemsSection.getKeys(false)) {
            final ConfigurationSection section = itemsSection.getConfigurationSection(rawItemKey);
            if (section == null) {
                continue;
            }

            final ItemKey itemKey = new ItemKey(rawItemKey);
            final String displayName = section.getString("display-name", rawItemKey);
            final ItemCategory category = ItemCategory.valueOf(section.getString("category", "UTILITY").toUpperCase());
            final ItemPolicyMode policyMode = ItemPolicyMode.valueOf(section.getString("policy", "DISABLED").toUpperCase());
            final boolean buyEnabled = section.getBoolean("buy-enabled", false);
            final boolean sellEnabled = section.getBoolean("sell-enabled", false);
            final long stockCap = section.getLong("stock-cap", 0L);
            final long turnoverAmountPerInterval = section.getLong("turnover-amount-per-interval", 0L);
            final BigDecimal buyPrice = this.getBigDecimal(section, "buy-price");
            final BigDecimal sellPrice = this.getBigDecimal(section, "sell-price");
            final List<SellPriceBand> sellPriceBands = this.parseSellPriceBands(section.getMapList("sell-price-bands"));

            items.put(itemKey, new ExchangeItemsConfig.RawItemEntry(
                itemKey,
                displayName,
                category,
                policyMode,
                buyEnabled,
                sellEnabled,
                stockCap,
                turnoverAmountPerInterval,
                buyPrice,
                sellPrice,
                sellPriceBands
            ));
        }

        return new ExchangeItemsConfig(items);
    }

    private List<SellPriceBand> parseSellPriceBands(final List<Map<?, ?>> rawBands) {
        final List<SellPriceBand> bands = new ArrayList<>();
        for (final Map<?, ?> rawBand : rawBands) {
            final double minFill = this.asDouble(rawBand.get("min-fill"), 0.0D);
            final double maxFill = this.asDouble(rawBand.get("max-fill"), 1.0D);
            final BigDecimal multiplier = this.asBigDecimal(rawBand.get("multiplier"));
            bands.add(new SellPriceBand(minFill, maxFill, multiplier));
        }
        return List.copyOf(bands);
    }

    private BigDecimal getBigDecimal(final ConfigurationSection section, final String path) {
        if (!section.contains(path)) {
            return null;
        }
        return this.asBigDecimal(section.get(path));
    }

    private BigDecimal asBigDecimal(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private double asDouble(final Object value, final double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private FileConfiguration loadYaml(final String resourceName) {
        final File file = new File(this.plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            this.plugin.saveResource(resourceName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/WorthImporter.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class WorthImporter {

    public Map<ItemKey, BigDecimal> importWorths(final WorthImportConfig config) {
        if (!config.enabled()) {
            return Map.of();
        }

        final File worthFile = new File(config.essentialsWorthFile());
        if (!worthFile.exists()) {
            if (config.ignoreMissingWorth()) {
                return Map.of();
            }
            throw new IllegalStateException("Essentials worth file not found: " + worthFile.getPath());
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(worthFile);
        final Map<ItemKey, BigDecimal> worths = new LinkedHashMap<>();

        for (final String key : yaml.getKeys(false)) {
            final Object value = yaml.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            if (!(value instanceof Number) && !(value instanceof String)) {
                continue;
            }

            final BigDecimal amount = this.asBigDecimal(value);
            if (amount == null) {
                continue;
            }

            worths.put(new ItemKey(this.normalizeWorthKey(key)), amount);
        }

        return Map.copyOf(worths);
    }

    private String normalizeWorthKey(final String rawKey) {
        if (rawKey.contains(":")) {
            return rawKey.toLowerCase();
        }
        return "minecraft:" + rawKey.toLowerCase();
    }

    private BigDecimal asBigDecimal(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogMergeService.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.Map;

public final class CatalogMergeService {

    public ExchangeCatalogEntry merge(
        final ExchangeItemsConfig.RawItemEntry rawEntry,
        final Map<ItemKey, BigDecimal> importedWorths,
        final WorthImportConfig worthImportConfig
    ) {
        final BigDecimal importedWorth = importedWorths.get(rawEntry.itemKey());
        final BigDecimal baseWorth = importedWorth != null ? importedWorth : BigDecimal.ZERO;

        final BigDecimal buyPrice = this.resolvePrice(
            rawEntry.buyPrice(),
            worthImportConfig.useWorthAsBaseValue() ? importedWorth : null
        );

        final BigDecimal sellPrice = this.resolvePrice(
            rawEntry.sellPrice(),
            worthImportConfig.useWorthAsBaseValue() ? importedWorth : null
        );

        return new ExchangeCatalogEntry(
            rawEntry.itemKey(),
            rawEntry.displayName(),
            rawEntry.category(),
            rawEntry.policyMode(),
            baseWorth,
            buyPrice,
            sellPrice,
            rawEntry.stockCap(),
            rawEntry.turnoverAmountPerInterval(),
            rawEntry.sellPriceBands(),
            rawEntry.buyEnabled(),
            rawEntry.sellEnabled()
        );
    }

    private BigDecimal resolvePrice(final BigDecimal explicitPrice, final BigDecimal importedFallback) {
        if (explicitPrice != null) {
            return explicitPrice;
        }
        if (importedFallback != null) {
            return importedFallback;
        }
        return BigDecimal.ZERO;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogLoader.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogLoader {

    private final WorthImporter worthImporter;
    private final CatalogMergeService mergeService;

    public CatalogLoader(final WorthImporter worthImporter, final CatalogMergeService mergeService) {
        this.worthImporter = worthImporter;
        this.mergeService = mergeService;
    }

    public ExchangeCatalog load(
        final ExchangeItemsConfig exchangeItemsConfig,
        final WorthImportConfig worthImportConfig
    ) {
        final Map<ItemKey, BigDecimal> importedWorths = this.worthImporter.importWorths(worthImportConfig);
        final Map<ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>();

        for (final ExchangeItemsConfig.RawItemEntry rawEntry : exchangeItemsConfig.items().values()) {
            final ExchangeCatalogEntry mergedEntry = this.mergeService.merge(rawEntry, importedWorths, worthImportConfig);
            entries.put(rawEntry.itemKey(), mergedEntry);
        }

        return new ExchangeCatalog(entries);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/item/CanonicalItemRules.java`

```java
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class CanonicalItemRules {

    public boolean isCanonicalForExchange(final ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        if (itemStack.getType() == Material.AIR) {
            return false;
        }
        if (itemStack.getAmount() <= 0) {
            return false;
        }
        return !this.hasForbiddenMeta(itemStack);
    }

    public ItemKey toItemKey(final ItemStack itemStack) {
        return new ItemKey("minecraft:" + itemStack.getType().name().toLowerCase());
    }

    private boolean hasForbiddenMeta(final ItemStack itemStack) {
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.hasDisplayName()) {
            return true;
        }
        if (meta.hasLore()) {
            return true;
        }
        if (meta.hasEnchants()) {
            return true;
        }
        if (meta.isUnbreakable()) {
            return true;
        }
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            return true;
        }
        return false;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/item/BukkitItemNormalizer.java`

```java
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class BukkitItemNormalizer implements ItemNormalizer {

    private final CanonicalItemRules canonicalItemRules;

    public BukkitItemNormalizer(final CanonicalItemRules canonicalItemRules) {
        this.canonicalItemRules = canonicalItemRules;
    }

    @Override
    public Optional<ItemKey> normalizeForExchange(final ItemStack itemStack) {
        if (!this.canonicalItemRules.isCanonicalForExchange(itemStack)) {
            return Optional.empty();
        }
        return Optional.of(this.canonicalItemRules.toItemKey(itemStack));
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/item/ItemValidationServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class ItemValidationServiceImpl implements ItemValidationService {

    private final ItemNormalizer itemNormalizer;
    private final ExchangeCatalog exchangeCatalog;

    public ItemValidationServiceImpl(final ItemNormalizer itemNormalizer, final ExchangeCatalog exchangeCatalog) {
        this.itemNormalizer = itemNormalizer;
        this.exchangeCatalog = exchangeCatalog;
    }

    @Override
    public ValidationResult validateForSell(final ItemStack itemStack) {
        if (itemStack == null) {
            return new ValidationResult(false, null, RejectionReason.ITEM_NOT_ELIGIBLE, "Item is null");
        }

        final Optional<ItemKey> normalized = this.itemNormalizer.normalizeForExchange(itemStack);
        if (normalized.isEmpty()) {
            return new ValidationResult(false, null, RejectionReason.INVALID_ITEM_STATE, "Item is not canonical for Exchange");
        }

        final ItemKey itemKey = normalized.get();
        final Optional<ExchangeCatalogEntry> entryOptional = this.exchangeCatalog.get(itemKey);
        if (entryOptional.isEmpty()) {
            return new ValidationResult(false, itemKey, RejectionReason.ITEM_NOT_ELIGIBLE, "Item is not in the Exchange catalog");
        }

        final ExchangeCatalogEntry entry = entryOptional.get();
        if (entry.policyMode() == ItemPolicyMode.DISABLED) {
            return new ValidationResult(false, itemKey, RejectionReason.ITEM_DISABLED, "Item is disabled");
        }
        if (!entry.sellEnabled()) {
            return new ValidationResult(false, itemKey, RejectionReason.SELL_NOT_ALLOWED, "Item is not sellable");
        }

        return new ValidationResult(true, itemKey, null, null);
    }

    @Override
    public ValidationResult validateForBuy(final ItemKey itemKey) {
        final Optional<ExchangeCatalogEntry> entryOptional = this.exchangeCatalog.get(itemKey);
        if (entryOptional.isEmpty()) {
            return new ValidationResult(false, itemKey, RejectionReason.ITEM_NOT_ELIGIBLE, "Item is not in the Exchange catalog");
        }

        final ExchangeCatalogEntry entry = entryOptional.get();
        if (entry.policyMode() == ItemPolicyMode.DISABLED) {
            return new ValidationResult(false, itemKey, RejectionReason.ITEM_DISABLED, "Item is disabled");
        }
        if (!entry.buyEnabled()) {
            return new ValidationResult(false, itemKey, RejectionReason.BUY_NOT_ALLOWED, "Item is not buyable");
        }

        return new ValidationResult(true, itemKey, null, null);
    }
}
```

---

## Wiring note for Commit 2A

After these file contents are added, the next wiring step is to update `ServiceRegistry` so it actually constructs:

* `ConfigLoader`
* `WorthImporter`
* `CatalogMergeService`
* `CatalogLoader`
* `CanonicalItemRules`
* `BukkitItemNormalizer`
* `ItemValidationServiceImpl`

That wiring belongs to the next small follow-up inside Commit 2A or at the start of Commit 2B, depending on how you want to stage it.

## Guardrail note

These files intentionally stay strict and narrow:

* no fuzzy item matching
* no custom item equivalence
* no dynamic market behavior
* no direct DB logic here

This is the clean base for the sell-path slice.
