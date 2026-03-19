# wild_economy GUI + Generated Category Pipeline Patch Files

This patch is based on the current pushed repo shape and folds in the next agreed direction:

* generated catalog becomes the runtime base catalog source
* `exchange-items.yml` remains the override layer
* `exchange-items.yml` supports wildcard keys
* root values continue to support wildcard keys
* runtime preserves **both**:

  * 7 top-level GUI groups
  * detailed generated subcategory
* browsing is stock-aware
* if a top-level group has `<= 45` visible items, open browse directly
* if it has `> 45` visible items, open a second-level submenu showing only non-empty generated subcategories plus `All`

This patch intentionally focuses on the pipeline and GUI flow, not cosmetic polish.

---

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/ItemCategory.java`

```java
package com.splatage.wild_economy.exchange.domain;

public enum ItemCategory {
    FARMING_AND_FOOD("Farming & Food"),
    MINING_AND_MINERALS("Mining & Minerals"),
    MOB_DROPS("Mob Drops"),
    BUILDING_MATERIALS("Building Materials"),
    REDSTONE_AND_UTILITY("Redstone & Utility"),
    COMBAT_AND_ADVENTURE("Combat & Adventure"),
    MISC("Misc");

    private final String displayName;

    ItemCategory(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/GeneratedItemCategory.java`

```java
package com.splatage.wild_economy.exchange.domain;

public enum GeneratedItemCategory {
    FARMING("Farming"),
    FOOD("Food"),
    ORES_AND_MINERALS("Ores & Minerals"),
    MOB_DROPS("Mob Drops"),
    WOODS("Woods"),
    STONE("Stone"),
    REDSTONE("Redstone"),
    TOOLS("Tools"),
    BREWING("Brewing"),
    TRANSPORT("Transport"),
    COMBAT("Combat"),
    NETHER("Nether"),
    END("End"),
    DECORATION("Decoration"),
    MISC("Misc");

    private final String displayName;

    GeneratedItemCategory(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/ExchangeCatalogEntry.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
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
    GeneratedItemCategory generatedCategory,
    ItemPolicyMode policyMode,
    BigDecimal baseWorth,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    long stockCap,
    long turnoverAmountPerInterval,
    List<SellPriceBand> sellPriceBands,
    boolean buyEnabled,
    boolean sellEnabled
) {
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/ExchangeCatalog.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
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

    public List<ExchangeCatalogEntry> byCategoryAndGeneratedCategory(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory
    ) {
        return this.entries.values().stream()
            .filter(entry -> entry.category() == category)
            .filter(entry -> entry.generatedCategory() == generatedCategory)
            .collect(Collectors.toList());
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/config/ExchangeItemsConfig.java`

```java
package com.splatage.wild_economy.config;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
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
        GeneratedItemCategory generatedCategory,
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

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/GeneratedCatalogImporter.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
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
            final GeneratedItemCategory generatedCategory = this.mapGeneratedCategory(
                section.getString("category", "MISC")
            );
            final ItemCategory topLevelCategory = this.mapTopLevelCategory(generatedCategory);

            final BigDecimal derivedValue = this.getBigDecimal(section, "derived-value");
            final BigDecimal rootValue = this.getBigDecimal(section, "root-value");
            final BigDecimal baseValue = derivedValue != null
                ? derivedValue
                : rootValue != null ? rootValue : BigDecimal.ZERO;

            final GeneratedPolicyMapping policyMapping = this.mapPolicy(section.getString("policy", "DISABLED"));

            entries.put(itemKey, new ExchangeCatalogEntry(
                itemKey,
                this.prettyDisplayName(itemKey.value()),
                topLevelCategory,
                generatedCategory,
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

    private GeneratedItemCategory mapGeneratedCategory(final String rawCategory) {
        final String normalized = rawCategory == null ? "MISC" : rawCategory.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FARMING" -> GeneratedItemCategory.FARMING;
            case "FOOD" -> GeneratedItemCategory.FOOD;
            case "ORES_AND_MINERALS" -> GeneratedItemCategory.ORES_AND_MINERALS;
            case "MOB_DROPS" -> GeneratedItemCategory.MOB_DROPS;
            case "WOODS" -> GeneratedItemCategory.WOODS;
            case "STONE" -> GeneratedItemCategory.STONE;
            case "REDSTONE" -> GeneratedItemCategory.REDSTONE;
            case "TOOLS" -> GeneratedItemCategory.TOOLS;
            case "BREWING" -> GeneratedItemCategory.BREWING;
            case "TRANSPORT" -> GeneratedItemCategory.TRANSPORT;
            case "COMBAT" -> GeneratedItemCategory.COMBAT;
            case "NETHER" -> GeneratedItemCategory.NETHER;
            case "END" -> GeneratedItemCategory.END;
            case "DECORATION" -> GeneratedItemCategory.DECORATION;
            case "MISC" -> GeneratedItemCategory.MISC;
            default -> GeneratedItemCategory.MISC;
        };
    }

    private ItemCategory mapTopLevelCategory(final GeneratedItemCategory generatedCategory) {
        return switch (generatedCategory) {
            case FARMING, FOOD -> ItemCategory.FARMING_AND_FOOD;
            case ORES_AND_MINERALS -> ItemCategory.MINING_AND_MINERALS;
            case MOB_DROPS -> ItemCategory.MOB_DROPS;
            case WOODS, STONE -> ItemCategory.BUILDING_MATERIALS;
            case REDSTONE, TOOLS, BREWING, TRANSPORT -> ItemCategory.REDSTONE_AND_UTILITY;
            case COMBAT, NETHER, END -> ItemCategory.COMBAT_AND_ADVENTURE;
            case DECORATION, MISC -> ItemCategory.MISC;
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
        for (final String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
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

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogMergeService.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.List;
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

        final String displayName = overrideEntry.displayName() != null
            ? overrideEntry.displayName()
            : baseEntry != null ? baseEntry.displayName() : overrideEntry.itemKey().value();

        final ItemCategory category = overrideEntry.category() != null
            ? overrideEntry.category()
            : baseEntry != null ? baseEntry.category() : ItemCategory.MISC;

        final GeneratedItemCategory generatedCategory = overrideEntry.generatedCategory() != null
            ? overrideEntry.generatedCategory()
            : baseEntry != null ? baseEntry.generatedCategory() : GeneratedItemCategory.MISC;

        final List sellPriceBands = overrideEntry.sellPriceBands() != null
            ? overrideEntry.sellPriceBands()
            : baseEntry != null ? baseEntry.sellPriceBands() : List.of();

        return new ExchangeCatalogEntry(
            overrideEntry.itemKey(),
            displayName,
            category,
            generatedCategory,
            overrideEntry.policyMode(),
            baseWorth,
            buyPrice,
            sellPrice,
            overrideEntry.stockCap(),
            overrideEntry.turnoverAmountPerInterval(),
            sellPriceBands,
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

## File: `src/main/java/com/splatage/wild_economy/config/ConfigLoader.java`

```java
package com.splatage.wild_economy.config;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
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
            final RawItemSpec spec = this.parseRawItemSpec(section);

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

    private RawItemSpec parseRawItemSpec(final ConfigurationSection section) {
        final String displayName = section.contains("display-name") ? section.getString("display-name") : null;
        final ItemCategory category = this.parseTopLevelCategory(section.getString("category", null));
        final GeneratedItemCategory generatedCategory = this.parseGeneratedCategory(section.getString("generated-category", null));
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
            generatedCategory,
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

    private ItemCategory parseTopLevelCategory(final String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return null;
        }
        final String normalized = rawCategory.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "FARMING", "FOOD", "FARMING_AND_FOOD" -> ItemCategory.FARMING_AND_FOOD;
            case "MINING", "ORES_AND_MINERALS", "MINING_AND_MINERALS" -> ItemCategory.MINING_AND_MINERALS;
            case "MOB_DROPS" -> ItemCategory.MOB_DROPS;
            case "BUILDING", "WOODS", "STONE", "BUILDING_MATERIALS" -> ItemCategory.BUILDING_MATERIALS;
            case "UTILITY", "REDSTONE", "TOOLS", "BREWING", "TRANSPORT", "REDSTONE_AND_UTILITY" -> ItemCategory.REDSTONE_AND_UTILITY;
            case "COMBAT", "NETHER", "END", "COMBAT_AND_ADVENTURE" -> ItemCategory.COMBAT_AND_ADVENTURE;
            case "DECORATION", "MISC" -> ItemCategory.MISC;
            default -> null;
        };
    }

    private GeneratedItemCategory parseGeneratedCategory(final String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return null;
        }
        final String normalized = rawCategory.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "FARMING" -> GeneratedItemCategory.FARMING;
            case "FOOD" -> GeneratedItemCategory.FOOD;
            case "ORES_AND_MINERALS" -> GeneratedItemCategory.ORES_AND_MINERALS;
            case "MOB_DROPS" -> GeneratedItemCategory.MOB_DROPS;
            case "WOODS" -> GeneratedItemCategory.WOODS;
            case "STONE" -> GeneratedItemCategory.STONE;
            case "REDSTONE" -> GeneratedItemCategory.REDSTONE;
            case "TOOLS" -> GeneratedItemCategory.TOOLS;
            case "BREWING" -> GeneratedItemCategory.BREWING;
            case "TRANSPORT" -> GeneratedItemCategory.TRANSPORT;
            case "COMBAT" -> GeneratedItemCategory.COMBAT;
            case "NETHER" -> GeneratedItemCategory.NETHER;
            case "END" -> GeneratedItemCategory.END;
            case "DECORATION" -> GeneratedItemCategory.DECORATION;
            case "MISC" -> GeneratedItemCategory.MISC;
            default -> null;
        };
    }

    private ExchangeItemsConfig.RawItemEntry toRawItemEntry(
        final ItemKey itemKey,
        final RawItemSpec spec
    ) {
        return new ExchangeItemsConfig.RawItemEntry(
            itemKey,
            spec.displayName(),
            spec.category(),
            spec.generatedCategory(),
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
        GeneratedItemCategory generatedCategory,
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

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseService.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.List;

public interface ExchangeBrowseService {
    List<ExchangeCatalogView> browseCategory(ItemCategory category, GeneratedItemCategory generatedCategory, int page, int pageSize);

    int countVisibleItems(ItemCategory category, GeneratedItemCategory generatedCategory);

    List<GeneratedItemCategory> listVisibleSubcategories(ItemCategory category);

    ExchangeItemView getItemView(ItemKey itemKey);
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.StockState;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ExchangeBrowseServiceImpl implements ExchangeBrowseService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;

    public ExchangeBrowseServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final int pageSize
    ) {
        final int safePage = Math.max(0, page);
        final int safePageSize = Math.max(1, pageSize);

        return this.visibleEntries(category, generatedCategory).stream()
            .skip((long) safePage * safePageSize)
            .limit(safePageSize)
            .map(visible -> new ExchangeCatalogView(
                visible.entry().itemKey(),
                visible.entry().displayName(),
                visible.entry().buyPrice(),
                visible.snapshot().stockCount(),
                visible.snapshot().stockState()
            ))
            .collect(Collectors.toList());
    }

    @Override
    public int countVisibleItems(final ItemCategory category, final GeneratedItemCategory generatedCategory) {
        return this.visibleEntries(category, generatedCategory).size();
    }

    @Override
    public List<GeneratedItemCategory> listVisibleSubcategories(final ItemCategory category) {
        return this.visibleEntries(category, null).stream()
            .map(visible -> visible.entry().generatedCategory())
            .filter(Objects::nonNull)
            .distinct()
            .sorted(Comparator.comparing(GeneratedItemCategory::displayName))
            .collect(Collectors.toList());
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));
        final StockSnapshot snapshot = this.stockService.getSnapshot(itemKey);
        return new ExchangeItemView(
            itemKey,
            entry.displayName(),
            entry.buyPrice(),
            snapshot.stockCount(),
            snapshot.stockCap(),
            snapshot.stockState(),
            entry.buyEnabled()
        );
    }

    private List<VisibleEntry> visibleEntries(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory
    ) {
        final List<ExchangeCatalogEntry> rawEntries = generatedCategory == null
            ? this.exchangeCatalog.byCategory(category)
            : this.exchangeCatalog.byCategoryAndGeneratedCategory(category, generatedCategory);

        return rawEntries.stream()
            .map(entry -> new VisibleEntry(entry, this.stockService.getSnapshot(entry.itemKey())))
            .filter(this::isPurchasableNow)
            .sorted(Comparator.comparing(visible -> visible.entry().displayName(), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    private boolean isPurchasableNow(final VisibleEntry visible) {
        final ExchangeCatalogEntry entry = visible.entry();
        if (!entry.buyEnabled()) {
            return false;
        }
        if (entry.policyMode() == ItemPolicyMode.UNLIMITED_BUY) {
            return true;
        }
        return visible.snapshot().stockState() != StockState.OUT_OF_STOCK;
    }

    private record VisibleEntry(
        ExchangeCatalogEntry entry,
        StockSnapshot snapshot
    ) {
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeService.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
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

    List<ExchangeCatalogView> browseCategory(ItemCategory category, GeneratedItemCategory generatedCategory, int page, int pageSize);

    int countVisibleItems(ItemCategory category, GeneratedItemCategory generatedCategory);

    List<GeneratedItemCategory> listVisibleSubcategories(ItemCategory category);

    ExchangeItemView getItemView(ItemKey itemKey);
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ExchangeServiceImpl implements ExchangeService {

    private final ExchangeBrowseService exchangeBrowseService;
    private final ExchangeBuyService exchangeBuyService;
    private final ExchangeSellService exchangeSellService;

    public ExchangeServiceImpl(
        final ExchangeBrowseService exchangeBrowseService,
        final ExchangeBuyService exchangeBuyService,
        final ExchangeSellService exchangeSellService
    ) {
        this.exchangeBrowseService = Objects.requireNonNull(exchangeBrowseService, "exchangeBrowseService");
        this.exchangeBuyService = Objects.requireNonNull(exchangeBuyService, "exchangeBuyService");
        this.exchangeSellService = Objects.requireNonNull(exchangeSellService, "exchangeSellService");
    }

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        return this.exchangeSellService.sellHand(playerId);
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        return this.exchangeSellService.sellAll(playerId);
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        return this.exchangeBuyService.buy(playerId, itemKey, amount);
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final int pageSize
    ) {
        return this.exchangeBrowseService.browseCategory(category, generatedCategory, page, pageSize);
    }

    @Override
    public int countVisibleItems(final ItemCategory category, final GeneratedItemCategory generatedCategory) {
        return this.exchangeBrowseService.countVisibleItems(category, generatedCategory);
    }

    @Override
    public List<GeneratedItemCategory> listVisibleSubcategories(final ItemCategory category) {
        return this.exchangeBrowseService.listVisibleSubcategories(category);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        return this.exchangeBrowseService.getItemView(itemKey);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/MenuSession.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.UUID;

public record MenuSession(
    UUID playerId,
    ViewType viewType,
    ItemCategory currentCategory,
    GeneratedItemCategory currentGeneratedCategory,
    int currentPage,
    ItemKey currentItemKey,
    boolean viaSubcategory
) {
    public enum ViewType {
        ROOT,
        SUBCATEGORY,
        BROWSE,
        DETAIL
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuRouter.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class ShopMenuRouter {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final Map<UUID, MenuSession> sessions = new HashMap<>();

    public ShopMenuRouter(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    public void openRoot(final Player player) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.ROOT,
            null,
            null,
            0,
            null,
            false
        ));
        this.exchangeRootMenu.open(player);
    }

    public void openSubcategory(final Player player, final ItemCategory category) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.SUBCATEGORY,
            category,
            null,
            0,
            null,
            false
        ));
        this.exchangeSubcategoryMenu.open(player, category);
    }

    public void openBrowse(
        final Player player,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.BROWSE,
            category,
            generatedCategory,
            page,
            null,
            viaSubcategory
        ));
        this.exchangeBrowseMenu.open(player, category, generatedCategory, page, viaSubcategory);
    }

    public void openDetail(final Player player, final ItemKey itemKey) {
        final MenuSession previous = this.sessions.get(player.getUniqueId());
        final ItemCategory category = previous == null ? null : previous.currentCategory();
        final GeneratedItemCategory generatedCategory = previous == null ? null : previous.currentGeneratedCategory();
        final int page = previous == null ? 0 : previous.currentPage();
        final boolean viaSubcategory = previous != null && previous.viaSubcategory();

        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.DETAIL,
            category,
            generatedCategory,
            page,
            itemKey,
            viaSubcategory
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
            case SUBCATEGORY -> this.openRoot(player);
            case BROWSE -> {
                if (session.viaSubcategory() && session.currentCategory() != null) {
                    this.openSubcategory(player, session.currentCategory());
                } else {
                    this.openRoot(player);
                }
            }
            case DETAIL -> {
                if (session.currentCategory() == null) {
                    this.openRoot(player);
                } else {
                    this.openBrowse(
                        player,
                        session.currentCategory(),
                        session.currentGeneratedCategory(),
                        session.currentPage(),
                        session.viaSubcategory()
                    );
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

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuListener.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class ShopMenuListener implements Listener {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final ShopMenuRouter shopMenuRouter;

    public ShopMenuListener(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu,
        final ShopMenuRouter shopMenuRouter
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
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
        if (!(title.equals("Shop") || title.startsWith("Shop - ") || title.startsWith("Buy - "))) {
            return;
        }

        final MenuSession session = this.shopMenuRouter.getSession(event.getWhoClicked().getUniqueId());
        if (session == null) {
            if (title.equals("Shop")) {
                this.exchangeRootMenu.handleClick(event);
            }
            return;
        }

        switch (session.viewType()) {
            case ROOT -> this.exchangeRootMenu.handleClick(event);
            case SUBCATEGORY -> {
                if (session.currentCategory() != null) {
                    this.exchangeSubcategoryMenu.handleClick(event, session.currentCategory());
                }
            }
            case BROWSE -> {
                if (session.currentCategory() != null) {
                    this.exchangeBrowseMenu.handleClick(
                        event,
                        session.currentCategory(),
                        session.currentGeneratedCategory(),
                        session.currentPage(),
                        session.viaSubcategory()
                    );
                }
            }
            case DETAIL -> {
                if (session.currentItemKey() != null) {
                    this.exchangeItemDetailMenu.handleClick(event, session.currentItemKey());
                    return;
                }
                final var current = event.getInventory().getItem(11);
                if (current != null && current.getType() != Material.AIR) {
                    this.exchangeItemDetailMenu.handleClick(event, this.toItemKey(current.getType()));
                }
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

    private ItemKey toItemKey(final Material material) {
        return new ItemKey("minecraft:" + material.name().toLowerCase());
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeRootMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
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

public final class ExchangeRootMenu {

    private final ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeRootMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player) {
        final Inventory inventory = Bukkit.createInventory(null, 27, "Shop");
        inventory.setItem(10, this.button(Material.BREAD, ItemCategory.FARMING_AND_FOOD.displayName()));
        inventory.setItem(11, this.button(Material.IRON_PICKAXE, ItemCategory.MINING_AND_MINERALS.displayName()));
        inventory.setItem(12, this.button(Material.BONE, ItemCategory.MOB_DROPS.displayName()));
        inventory.setItem(13, this.button(Material.BRICKS, ItemCategory.BUILDING_MATERIALS.displayName()));
        inventory.setItem(14, this.button(Material.REDSTONE, ItemCategory.REDSTONE_AND_UTILITY.displayName()));
        inventory.setItem(15, this.button(Material.DIAMOND_SWORD, ItemCategory.COMBAT_AND_ADVENTURE.displayName()));
        inventory.setItem(16, this.button(Material.CHEST, ItemCategory.MISC.displayName()));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> this.openCategory(player, ItemCategory.FARMING_AND_FOOD);
            case 11 -> this.openCategory(player, ItemCategory.MINING_AND_MINERALS);
            case 12 -> this.openCategory(player, ItemCategory.MOB_DROPS);
            case 13 -> this.openCategory(player, ItemCategory.BUILDING_MATERIALS);
            case 14 -> this.openCategory(player, ItemCategory.REDSTONE_AND_UTILITY);
            case 15 -> this.openCategory(player, ItemCategory.COMBAT_AND_ADVENTURE);
            case 16 -> this.openCategory(player, ItemCategory.MISC);
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void openCategory(final Player player, final ItemCategory category) {
        final int visibleCount = this.exchangeService.countVisibleItems(category, null);
        final List<GeneratedItemCategory> subcategories = this.exchangeService.listVisibleSubcategories(category);

        if (visibleCount > 45 && subcategories.size() > 1) {
            this.shopMenuRouter.openSubcategory(player, category);
            return;
        }

        this.shopMenuRouter.openBrowse(player, category, null, 0, false);
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

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeSubcategoryMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
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

public final class ExchangeSubcategoryMenu {

    private static final int[] SUBCATEGORY_SLOTS = {11, 12, 13, 14, 15, 20, 21, 23, 24};

    private final ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeSubcategoryMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player, final ItemCategory category) {
        final Inventory inventory = Bukkit.createInventory(null, 27, "Shop - " + category.displayName() + " Types");
        inventory.setItem(10, this.button(Material.CHEST, "All"));

        final List<GeneratedItemCategory> subcategories = this.exchangeService.listVisibleSubcategories(category);
        for (int i = 0; i < subcategories.size() && i < SUBCATEGORY_SLOTS.length; i++) {
            final GeneratedItemCategory generatedItemCategory = subcategories.get(i);
            inventory.setItem(SUBCATEGORY_SLOTS[i], this.button(
                this.icon(generatedItemCategory),
                generatedItemCategory.displayName()
            ));
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
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeBrowseMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
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

    public void open(
        final Player player,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        final Inventory inventory = Bukkit.createInventory(null, 54, this.title(category, generatedCategory));
        final List<ExchangeCatalogView> entries = this.exchangeService.browseCategory(category, generatedCategory, page, 45);

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

    public void handleClick(
        final InventoryClickEvent event,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return;
            }
            final ItemKey itemKey = this.toItemKey(clicked.getType());
            this.shopMenuRouter.openDetail(player, itemKey);
            return;
        }

        switch (slot) {
            case 45 -> {
                if (viaSubcategory) {
                    this.shopMenuRouter.openSubcategory(player, category);
                } else {
                    this.shopMenuRouter.openRoot(player);
                }
            }
            case 49 -> player.closeInventory();
            case 53 -> this.shopMenuRouter.openBrowse(player, category, generatedCategory, page + 1, viaSubcategory);
            default -> {
            }
        }
    }

    private String title(final ItemCategory category, final GeneratedItemCategory generatedCategory) {
        if (generatedCategory == null) {
            return "Shop - " + category.displayName();
        }
        return "Shop - " + category.displayName() + " / " + generatedCategory.displayName();
    }

    private ItemStack catalogItem(final ExchangeCatalogView view) {
        final Material material = this.resolveMaterial(view.itemKey());
        final ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(view.displayName());
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

    private ItemKey toItemKey(final Material material) {
        return new ItemKey("minecraft:" + material.name().toLowerCase());
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
import com.splatage.wild_economy.gui.ExchangeSubcategoryMenu;
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

        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(this.exchangeService);
        final ExchangeSubcategoryMenu subcategoryMenu = new ExchangeSubcategoryMenu(this.exchangeService);
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService);
        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService);

        this.shopMenuRouter = new ShopMenuRouter(rootMenu, subcategoryMenu, browseMenu, itemDetailMenu);
        rootMenu.setShopMenuRouter(this.shopMenuRouter);
        subcategoryMenu.setShopMenuRouter(this.shopMenuRouter);
        browseMenu.setShopMenuRouter(this.shopMenuRouter);
        itemDetailMenu.setShopMenuRouter(this.shopMenuRouter);

        this.plugin.getServer().getPluginManager().registerEvents(
            new ShopMenuListener(rootMenu, subcategoryMenu, browseMenu, itemDetailMenu, this.shopMenuRouter),
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

This patch intentionally leaves `ExchangeItemDetailMenu.java` untouched.
It already works off `ExchangeService.getItemView(...)` and does not need to know about the new second-level grouping.

This patch also keeps override rows as the current repo model: full `RawItemEntry` rows.
The merge service preserves base generated category where the override does not specify one, but it does not attempt to implement sparse field patches beyond that.
