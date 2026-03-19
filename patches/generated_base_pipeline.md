# wild_economy Generated Base Pipeline Patch Files

This patch set is based on the current pushed repo shape and wires the generated catalog into the live runtime pipeline.

What it changes:

* `generated/generated-catalog.yml` becomes the **base runtime catalog source**
* `exchange-items.yml` becomes the **override layer**
* `exchange-items.yml` supports wildcard item keys
* exact override keys still win over wildcard matches
* runtime root value import also supports wildcards

Current live problem this fixes:

* the shop GUI can only display items present in `exchangeCatalog`
* `exchangeCatalog` is currently built only from `exchange-items.yml`
* generated items never reach the browse service or GUI

This patch changes the runtime flow to:

`generated base + exchange-items overrides -> exchangeCatalog -> browse service -> GUI`

## Important current limitation

This keeps override entries as **full replacement rows** in the current runtime model.
So `exchange-items.yml` is now an override layer, but still expressed using full `RawItemEntry` rows, not sparse per-field patch objects.

That keeps the patch aligned to the pushed repo without widening the config/domain model more than necessary.

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/GeneratedCatalogImporter.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class GeneratedCatalogImporter {

    public Map<ItemKey, ExchangeCatalogEntry> importGeneratedCatalog(final File generatedCatalogFile) {
        if (generatedCatalogFile == null) {
            throw new IllegalArgumentException("generatedCatalogFile must not be null");
        }
        if (!generatedCatalogFile.exists() || !generatedCatalogFile.isFile()) {
            return Map.of();
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(generatedCatalogFile);
        final ConfigurationSection entriesSection = yaml.getConfigurationSection("entries");
        if (entriesSection == null) {
            return Map.of();
        }

        final Map<ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>();

        for (final String rawItemKey : entriesSection.getKeys(false)) {
            final ConfigurationSection section = entriesSection.getConfigurationSection(rawItemKey);
            if (section == null) {
                continue;
            }

            final ItemKey itemKey = new ItemKey(this.normalizeItemKey(rawItemKey));
            final BigDecimal derivedValue = this.getBigDecimal(section, "derived-value");
            final BigDecimal rootValue = this.getBigDecimal(section, "root-value");
            final BigDecimal baseValue = derivedValue != null
                ? derivedValue
                : rootValue != null ? rootValue : BigDecimal.ZERO;

            final GeneratedPolicyMapping policyMapping = this.mapPolicy(
                section.getString("policy", "DISABLED")
            );

            entries.put(itemKey, new ExchangeCatalogEntry(
                itemKey,
                this.prettyDisplayName(itemKey.value()),
                this.mapCategory(section.getString("category", "MISC")),
                policyMapping.policyMode(),
                baseValue,
                baseValue,
                baseValue,
                0L,
                0L,
                List.of(),
                policyMapping.buyEnabled(),
                policyMapping.sellEnabled()
            ));
        }

        return Map.copyOf(entries);
    }

    private ItemCategory mapCategory(final String rawCategory) {
        final String normalized = rawCategory == null ? "MISC" : rawCategory.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FARMING", "FOOD" -> ItemCategory.FARMING;
            case "ORES_AND_MINERALS", "REDSTONE" -> ItemCategory.MINING;
            case "MOB_DROPS" -> ItemCategory.MOB_DROPS;
            case "WOODS", "STONE", "DECORATION" -> ItemCategory.BUILDING;
            case "NETHER", "END", "TOOLS", "COMBAT", "BREWING", "TRANSPORT", "MISC" -> ItemCategory.UTILITY;
            default -> ItemCategory.UTILITY;
        };
    }

    private GeneratedPolicyMapping mapPolicy(final String rawPolicy) {
        final String normalized = rawPolicy == null ? "DISABLED" : rawPolicy.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ALWAYS_AVAILABLE" -> new GeneratedPolicyMapping(ItemPolicyMode.UNLIMITED_BUY, true, false);
            case "EXCHANGE" -> new GeneratedPolicyMapping(ItemPolicyMode.PLAYER_STOCKED, true, true);
            case "SELL_ONLY" -> new GeneratedPolicyMapping(ItemPolicyMode.PLAYER_STOCKED, false, true);
            case "DISABLED" -> new GeneratedPolicyMapping(ItemPolicyMode.DISABLED, false, false);
            default -> new GeneratedPolicyMapping(ItemPolicyMode.DISABLED, false, false);
        };
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
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeItemKey(final String rawItemKey) {
        String key = rawItemKey.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        return key;
    }

    private String prettyDisplayName(final String itemKey) {
        final String plain = itemKey.replace("minecraft:", "");
        final String[] parts = plain.split("_");
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                builder.append(parts[i].substring(1));
            }
        }
        return builder.toString();
    }

    private record GeneratedPolicyMapping(
        ItemPolicyMode policyMode,
        boolean buyEnabled,
        boolean sellEnabled
    ) {
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogMergeService.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.Map;

public final class CatalogMergeService {

    public ExchangeCatalogEntry merge(
        final ExchangeCatalogEntry baseEntry,
        final ExchangeItemsConfig.RawItemEntry overrideEntry,
        final Map<ItemKey, BigDecimal> importedRootValues
    ) {
        final BigDecimal rootValue = importedRootValues.get(overrideEntry.itemKey());
        final BigDecimal baseWorth = this.resolveBaseWorth(baseEntry, rootValue);
        final BigDecimal buyPrice = this.resolvePrice(overrideEntry.buyPrice(), baseEntry != null ? baseEntry.buyPrice() : null, rootValue);
        final BigDecimal sellPrice = this.resolvePrice(overrideEntry.sellPrice(), baseEntry != null ? baseEntry.sellPrice() : null, rootValue);

        return new ExchangeCatalogEntry(
            overrideEntry.itemKey(),
            overrideEntry.displayName(),
            overrideEntry.category(),
            overrideEntry.policyMode(),
            baseWorth,
            buyPrice,
            sellPrice,
            overrideEntry.stockCap(),
            overrideEntry.turnoverAmountPerInterval(),
            overrideEntry.sellPriceBands(),
            overrideEntry.buyEnabled(),
            overrideEntry.sellEnabled()
        );
    }

    private BigDecimal resolveBaseWorth(
        final ExchangeCatalogEntry baseEntry,
        final BigDecimal rootValue
    ) {
        if (baseEntry != null && baseEntry.baseWorth() != null) {
            return baseEntry.baseWorth();
        }
        if (rootValue != null) {
            return rootValue;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolvePrice(
        final BigDecimal explicitPrice,
        final BigDecimal baseCatalogPrice,
        final BigDecimal rootValueFallback
    ) {
        if (explicitPrice != null) {
            return explicitPrice;
        }
        if (baseCatalogPrice != null) {
            return baseCatalogPrice;
        }
        if (rootValueFallback != null) {
            return rootValueFallback;
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
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogLoader {

    private final GeneratedCatalogImporter generatedCatalogImporter;
    private final RootValueImporter rootValueImporter;
    private final CatalogMergeService mergeService;

    public CatalogLoader(
        final GeneratedCatalogImporter generatedCatalogImporter,
        final RootValueImporter rootValueImporter,
        final CatalogMergeService mergeService
    ) {
        this.generatedCatalogImporter = generatedCatalogImporter;
        this.rootValueImporter = rootValueImporter;
        this.mergeService = mergeService;
    }

    public ExchangeCatalog load(
        final ExchangeItemsConfig exchangeItemsConfig,
        final File rootValuesFile,
        final File generatedCatalogFile
    ) {
        final Map<ItemKey, BigDecimal> importedRootValues = this.rootValueImporter.importRootValues(rootValuesFile);
        final Map<ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>(
            this.generatedCatalogImporter.importGeneratedCatalog(generatedCatalogFile)
        );

        for (final ExchangeItemsConfig.RawItemEntry rawEntry : exchangeItemsConfig.items().values()) {
            final ExchangeCatalogEntry baseEntry = entries.get(rawEntry.itemKey());
            final ExchangeCatalogEntry mergedEntry = this.mergeService.merge(baseEntry, rawEntry, importedRootValues);
            entries.put(rawEntry.itemKey(), mergedEntry);
        }

        return new ExchangeCatalog(entries);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/RootValueImporter.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class RootValueImporter {

    public Map<ItemKey, BigDecimal> importRootValues(final File rootValuesFile) {
        if (rootValuesFile == null) {
            throw new IllegalArgumentException("rootValuesFile must not be null");
        }
        if (!rootValuesFile.exists()) {
            throw new IllegalStateException("Root values file not found: " + rootValuesFile.getPath());
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(rootValuesFile);
        final ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
        if (itemsSection == null) {
            return Map.of();
        }

        final Map<ItemKey, BigDecimal> exactValues = new LinkedHashMap<>();
        final List<WildcardRule> wildcardRules = new ArrayList<>();

        int order = 0;
        for (final String rawKey : itemsSection.getKeys(false)) {
            final Object value = itemsSection.get(rawKey);
            if (!(value instanceof Number) && !(value instanceof String)) {
                continue;
            }

            final BigDecimal amount = this.asBigDecimal(value);
            if (amount == null) {
                continue;
            }

            final String normalizedKey = this.normalizeItemKey(rawKey);
            if (this.isWildcardPattern(normalizedKey)) {
                wildcardRules.add(new WildcardRule(
                    normalizedKey,
                    this.compileGlob(normalizedKey),
                    amount,
                    this.wildcardSpecificity(normalizedKey),
                    order++
                ));
                continue;
            }

            exactValues.put(new ItemKey(normalizedKey), amount);
            order++;
        }

        final Map<ItemKey, BigDecimal> expanded = new LinkedHashMap<>();

        wildcardRules.sort(
            Comparator.comparingInt(WildcardRule::specificity)
                .thenComparingInt(WildcardRule::order)
        );

        for (final WildcardRule rule : wildcardRules) {
            for (final Material material : Material.values()) {
                if (!this.isIncludedMaterial(material)) {
                    continue;
                }

                final String itemKey = this.normalizeItemKey(material);
                if (rule.matches(itemKey)) {
                    expanded.put(new ItemKey(itemKey), rule.value());
                }
            }
        }

        expanded.putAll(exactValues);
        return Map.copyOf(expanded);
    }

    private boolean isIncludedMaterial(final Material material) {
        if (material == Material.AIR) {
            return false;
        }
        if (!material.isItem()) {
            return false;
        }
        return !material.isLegacy();
    }

    private boolean isWildcardPattern(final String key) {
        return key.indexOf('*') >= 0;
    }

    private int wildcardSpecificity(final String key) {
        int specificity = 0;
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) != '*') {
                specificity++;
            }
        }
        return specificity;
    }

    private Pattern compileGlob(final String glob) {
        final StringBuilder regex = new StringBuilder(glob.length() * 2);
        regex.append('^');
        for (int i = 0; i < glob.length(); i++) {
            final char ch = glob.charAt(i);
            if (ch == '*') {
                regex.append(".*");
                continue;
            }
            if ("\\.^$|?+()[]{}".indexOf(ch) >= 0) {
                regex.append('\\');
            }
            regex.append(ch);
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    private String normalizeItemKey(final String rawKey) {
        String lowered = rawKey.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!lowered.contains(":")) {
            lowered = "minecraft:" + lowered;
        }
        return lowered;
    }

    private String normalizeItemKey(final Material material) {
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
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

    private record WildcardRule(
        String pattern,
        Pattern regex,
        BigDecimal value,
        int specificity,
        int order
    ) {
        private boolean matches(final String itemKey) {
            return this.regex.matcher(itemKey).matches();
        }
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Material;
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

    public ExchangeItemsConfig loadExchangeItemsConfig() {
        final FileConfiguration config = this.loadYaml("exchange-items.yml");
        final ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            throw new IllegalStateException("exchange-items.yml is missing the 'items' section");
        }

        final Map<ItemKey, ExchangeItemsConfig.RawItemEntry> exactEntries = new LinkedHashMap<>();
        final List<WildcardItemRule> wildcardRules = new ArrayList<>();

        int order = 0;
        for (final String rawItemKey : itemsSection.getKeys(false)) {
            final ConfigurationSection section = itemsSection.getConfigurationSection(rawItemKey);
            if (section == null) {
                continue;
            }

            final String normalizedItemKey = this.normalizeItemKey(rawItemKey);
            final RawItemSpec spec = this.parseRawItemSpec(section, normalizedItemKey);

            if (this.isWildcardPattern(normalizedItemKey)) {
                wildcardRules.add(new WildcardItemRule(
                    normalizedItemKey,
                    this.compileGlob(normalizedItemKey),
                    this.wildcardSpecificity(normalizedItemKey),
                    order++,
                    spec
                ));
                continue;
            }

            final ItemKey itemKey = new ItemKey(normalizedItemKey);
            exactEntries.put(itemKey, this.toRawItemEntry(itemKey, spec));
            order++;
        }

        final Map<ItemKey, ExchangeItemsConfig.RawItemEntry> expandedEntries = new LinkedHashMap<>();

        wildcardRules.sort(
            Comparator.comparingInt(WildcardItemRule::specificity)
                .thenComparingInt(WildcardItemRule::order)
        );

        for (final WildcardItemRule rule : wildcardRules) {
            for (final Material material : Material.values()) {
                if (!this.isIncludedMaterial(material)) {
                    continue;
                }

                final String normalizedItemKey = this.normalizeItemKey(material);
                if (!rule.matches(normalizedItemKey)) {
                    continue;
                }

                final ItemKey itemKey = new ItemKey(normalizedItemKey);
                expandedEntries.put(itemKey, this.toRawItemEntry(itemKey, rule.spec()));
            }
        }

        expandedEntries.putAll(exactEntries);
        return new ExchangeItemsConfig(expandedEntries);
    }

    private RawItemSpec parseRawItemSpec(final ConfigurationSection section, final String normalizedItemKey) {
        final String displayName = section.contains("display-name")
            ? section.getString("display-name")
            : normalizedItemKey;

        final ItemCategory category = ItemCategory.valueOf(
            section.getString("category", "UTILITY").toUpperCase(Locale.ROOT)
        );

        final ItemPolicyMode policyMode = ItemPolicyMode.valueOf(
            section.getString("policy", "DISABLED").toUpperCase(Locale.ROOT)
        );

        final boolean buyEnabled = section.getBoolean("buy-enabled", false);
        final boolean sellEnabled = section.getBoolean("sell-enabled", false);
        final long stockCap = section.getLong("stock-cap", 0L);
        final long turnoverAmountPerInterval = section.getLong("turnover-amount-per-interval", 0L);
        final BigDecimal buyPrice = this.getBigDecimal(section, "buy-price");
        final BigDecimal sellPrice = this.getBigDecimal(section, "sell-price");
        final List<SellPriceBand> sellPriceBands = this.parseSellPriceBands(section.getMapList("sell-price-bands"));

        return new RawItemSpec(
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
        );
    }

    private ExchangeItemsConfig.RawItemEntry toRawItemEntry(
        final ItemKey itemKey,
        final RawItemSpec spec
    ) {
        final String displayName = spec.displayName() != null ? spec.displayName() : itemKey.value();
        return new ExchangeItemsConfig.RawItemEntry(
            itemKey,
            displayName,
            spec.category(),
            spec.policyMode(),
            spec.buyEnabled(),
            spec.sellEnabled(),
            spec.stockCap(),
            spec.turnoverAmountPerInterval(),
            spec.buyPrice(),
            spec.sellPrice(),
            spec.sellPriceBands()
        );
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

    private boolean isWildcardPattern(final String itemKey) {
        return itemKey.indexOf('*') >= 0;
    }

    private int wildcardSpecificity(final String itemKey) {
        int specificity = 0;
        for (int i = 0; i < itemKey.length(); i++) {
            if (itemKey.charAt(i) != '*') {
                specificity++;
            }
        }
        return specificity;
    }

    private Pattern compileGlob(final String glob) {
        final StringBuilder regex = new StringBuilder(glob.length() * 2);
        regex.append('^');
        for (int i = 0; i < glob.length(); i++) {
            final char ch = glob.charAt(i);
            if (ch == '*') {
                regex.append(".*");
                continue;
            }
            if ("\\.^$|?+()[]{}".indexOf(ch) >= 0) {
                regex.append('\\');
            }
            regex.append(ch);
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    private boolean isIncludedMaterial(final Material material) {
        if (material == Material.AIR) {
            return false;
        }
        if (!material.isItem()) {
            return false;
        }
        return !material.isLegacy();
    }

    private String normalizeItemKey(final String rawItemKey) {
        String key = rawItemKey.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        return key;
    }

    private String normalizeItemKey(final Material material) {
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
    }

    private record RawItemSpec(
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

    private record WildcardItemRule(
        String pattern,
        Pattern regex,
        int specificity,
        int order,
        RawItemSpec spec
    ) {
        private boolean matches(final String itemKey) {
            return this.regex.matcher(itemKey).matches();
        }
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
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.VaultEconomyGateway;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.CatalogMergeService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.GeneratedCatalogImporter;
import com.splatage.wild_economy.exchange.catalog.RootValueImporter;
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
import java.io.File;
import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
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

        final File rootValuesFile = new File(this.plugin.getDataFolder(), "root-values.yml");
        final File generatedCatalogFile = new File(new File(this.plugin.getDataFolder(), "generated"), "generated-catalog.yml");

        if (!generatedCatalogFile.exists()) {
            this.plugin.getLogger().warning(
                "generated/generated-catalog.yml not found. Runtime catalog will fall back to exchange-items.yml overrides only."
            );
        }

        final GeneratedCatalogImporter generatedCatalogImporter = new GeneratedCatalogImporter();
        final RootValueImporter rootValueImporter = new RootValueImporter();
        final CatalogMergeService catalogMergeService = new CatalogMergeService();
        final CatalogLoader catalogLoader = new CatalogLoader(
            generatedCatalogImporter,
            rootValueImporter,
            catalogMergeService
        );

        this.exchangeCatalog = Objects.requireNonNull(
            catalogLoader.load(this.exchangeItemsConfig, rootValuesFile, generatedCatalogFile),
            "exchangeCatalog"
        );

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
        final RegisteredServiceProvider<Economy> registration =
            this.plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }

        return new VaultEconomyGateway(registration.getProvider());
    }
}
```

---

## Notes

This patch deliberately does **not** change the GUI classes.
That is because the GUI already reads from `exchangeCatalog` through the browse service.
Once `exchangeCatalog` is built from `generated base + overrides`, the GUI will automatically see generated items.

## Current assumptions preserved

* `exchange-items.yml` override rows are still full runtime rows in the current model
* generated categories are mapped into the repo's current broad runtime categories
* generated policies are mapped into the repo's current `ItemPolicyMode`
* `SELL_ONLY` generated entries map to `PLAYER_STOCKED` with `buyEnabled=false` and `sellEnabled=true`
* if `generated/generated-catalog.yml` is missing, runtime falls back to override-only behavior with a warning
