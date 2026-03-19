# wild_economy Exchange Items Wildcard Patch Files

This patch extends `exchange-items.yml` to support wildcard item keys, based on the current pushed repo shape.

Current repo behavior:

* `ConfigLoader.loadExchangeItemsConfig()` reads `exchange-items.yml` into exact `ItemKey -> RawItemEntry` values only. ([raw repo source])
* `ExchangeItemsConfig` is already just a map wrapper, so the cleanest patch is to expand wildcard entries into exact `RawItemEntry` entries at load time.

This means:

* exact override keys still win over wildcard matches
* more specific wildcard patterns beat broader wildcard patterns
* wildcard entries are expanded over the Bukkit `Material` universe
* the rest of the runtime code can stay unchanged

Supported examples:

* `minecraft:*_sapling`
* `minecraft:*_sign`
* `*_sapling`
* `oak_*`

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

## Example `exchange-items.yml`

```yaml
items:
  minecraft:*_sapling:
    category: WOODS
    policy: PLAYER_STOCKED
    buy-enabled: true
    sell-enabled: true
    stock-cap: 5000
    turnover-amount-per-interval: 100
    buy-price: 8.0
    sell-price: 5.0
    sell-price-bands: []

  minecraft:jungle_sapling:
    category: WOODS
    policy: DISABLED
    buy-enabled: false
    sell-enabled: false
    stock-cap: 0
    turnover-amount-per-interval: 0
    buy-price: 0.0
    sell-price: 0.0
    sell-price-bands: []
```

Expected behavior:

* all saplings match the wildcard rule
* `minecraft:jungle_sapling` exact entry wins over the wildcard

---

## Notes

This patch keeps `ExchangeItemsConfig.java` unchanged.

Reason:

* the pushed repo already treats `ExchangeItemsConfig` as a plain map wrapper
* wildcard expansion in `ConfigLoader` lets the rest of the runtime continue using exact `ItemKey -> RawItemEntry` maps without further changes

If you later move fully to `generated base + override merge`, this loader can still serve as the override-expansion layer before merge.
