package com.splatage.wild_economy.config;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigLoader {

    private static final String[] LEGACY_RUNTIME_ITEM_FIELDS = {
            "eco-envelope",
            "buy-price",
            "sell-price",
            "sell-price-bands",
            "stock-profile",
            "policy-profile",
            "admin-policy",
            "runtime-policy",
            "stock-backed",
            "unlimited-buy",
            "requires-player-stock-to-buy"
    };

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
                config.getBoolean("logging.debug", false),
                config.getBoolean("buy-delivery.use-held-shulker", false),
                config.getBoolean("buy-delivery.use-looked-at-container", false),
                config.getBoolean("buy-delivery.use-player-inventory", true),
                config.getBoolean("buy-delivery.drop-at-feet", false)
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
        final ConfigurationSection itemsSection = this.requireSection(
                config,
                "items",
                "exchange-items.yml is missing the 'items' section"
        );

        final Map<ItemKey, ExchangeItemsConfig.RawItemEntry> entries = new LinkedHashMap<>();
        for (final String rawItemKey : itemsSection.getKeys(false)) {
            final ConfigurationSection section = itemsSection.getConfigurationSection(rawItemKey);
            if (section == null) {
                continue;
            }

            final String normalizedItemKey = this.normalizeItemKey(rawItemKey);
            if (normalizedItemKey.indexOf('*') >= 0) {
                throw new IllegalStateException(
                        "exchange-items.yml contains wildcard runtime key '" + rawItemKey
                                + "'. Published runtime catalogs must contain only concrete item keys."
                );
            }

            this.rejectLegacyRuntimeFields(normalizedItemKey, section);

            final ItemKey itemKey = new ItemKey(normalizedItemKey);
            if (entries.containsKey(itemKey)) {
                throw new IllegalStateException(
                        "exchange-items.yml defines duplicate runtime item '" + normalizedItemKey + "'"
                );
            }

            entries.put(itemKey, this.parseRuntimeItemSpec(itemKey, section));
        }

        return new ExchangeItemsConfig(entries);
    }

    private ExchangeItemsConfig.RawItemEntry parseRuntimeItemSpec(
            final ItemKey itemKey,
            final ConfigurationSection section
    ) {
        final String displayName = this.getOptionalString(section, "display-name");
        final ItemCategory category = this.requireTopLevelCategory(itemKey, section, "category");
        final GeneratedItemCategory generatedCategory = this.parseGeneratedCategory(section.getString("generated-category", null));
        final ItemPolicyMode policyMode = this.requirePolicyMode(itemKey, section, "policy");
        final boolean buyEnabled = this.requireBoolean(itemKey, section, "buy-enabled");
        final boolean sellEnabled = this.requireBoolean(itemKey, section, "sell-enabled");
        final long stockCap = this.requireNonNegativeLong(itemKey, section, "stock-cap");
        final long turnoverAmountPerInterval = this.requireNonNegativeLong(itemKey, section, "turnover-amount-per-interval");
        final BigDecimal baseWorth = this.requireNonNegativeBigDecimal(itemKey, section, "base-worth");
        final ConfigurationSection ecoSection = this.requireSection(
                section,
                "eco",
                "exchange-items.yml item '" + itemKey.value() + "' is missing the required 'eco' section"
        );

        return new ExchangeItemsConfig.RawItemEntry(
                itemKey,
                displayName,
                category,
                generatedCategory,
                policyMode,
                buyEnabled,
                sellEnabled,
                stockCap,
                turnoverAmountPerInterval,
                baseWorth,
                this.parseResolvedEco(itemKey, ecoSection)
        );
    }

    private ExchangeItemsConfig.ResolvedEcoEntry parseResolvedEco(
            final ItemKey itemKey,
            final ConfigurationSection ecoSection
    ) {
        final long minStockInclusive = this.requireNonNegativeLong(itemKey, ecoSection, "min-stock");
        final long maxStockInclusive = this.requireNonNegativeLong(itemKey, ecoSection, "max-stock");
        if (maxStockInclusive < minStockInclusive) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' has eco.max-stock < eco.min-stock"
            );
        }

        final BigDecimal buyPriceAtMinStock = this.requireNonNegativeBigDecimal(itemKey, ecoSection, "buy-price-at-min-stock");
        final BigDecimal buyPriceAtMaxStock = this.requireNonNegativeBigDecimal(itemKey, ecoSection, "buy-price-at-max-stock");
        final BigDecimal sellPriceAtMinStock = this.requireNonNegativeBigDecimal(itemKey, ecoSection, "sell-price-at-min-stock");
        final BigDecimal sellPriceAtMaxStock = this.requireNonNegativeBigDecimal(itemKey, ecoSection, "sell-price-at-max-stock");

        return new ExchangeItemsConfig.ResolvedEcoEntry(
                minStockInclusive,
                maxStockInclusive,
                buyPriceAtMinStock,
                buyPriceAtMaxStock,
                sellPriceAtMinStock,
                sellPriceAtMaxStock
        );
    }

    private void rejectLegacyRuntimeFields(final String normalizedItemKey, final ConfigurationSection section) {
        for (final String legacyField : LEGACY_RUNTIME_ITEM_FIELDS) {
            if (section.contains(legacyField)) {
                throw new IllegalStateException(
                        "exchange-items.yml item '" + normalizedItemKey + "' uses legacy runtime field '"
                                + legacyField
                                + "'. The published runtime catalog must contain resolved eco data under 'eco' instead."
                );
            }
        }
    }

    private ItemCategory requireTopLevelCategory(
            final ItemKey itemKey,
            final ConfigurationSection section,
            final String path
    ) {
        final ItemCategory category = this.parseTopLevelCategory(this.requireString(itemKey, section, path));
        if (category == null) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' has invalid category '" + section.getString(path) + "'"
            );
        }
        return category;
    }

    private ItemPolicyMode requirePolicyMode(
            final ItemKey itemKey,
            final ConfigurationSection section,
            final String path
    ) {
        final String rawValue = this.requireString(itemKey, section, path);
        try {
            return ItemPolicyMode.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' has invalid policy '" + rawValue + "'",
                    ex
            );
        }
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

    private String getOptionalString(final ConfigurationSection section, final String path) {
        if (!section.contains(path)) {
            return null;
        }
        final String value = section.getString(path);
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireString(
            final ItemKey itemKey,
            final ConfigurationSection section,
            final String path
    ) {
        final String value = this.getOptionalString(section, path);
        if (value == null) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' is missing required string field '" + path + "'"
            );
        }
        return value;
    }

    private boolean requireBoolean(
            final ItemKey itemKey,
            final ConfigurationSection section,
            final String path
    ) {
        if (!section.contains(path)) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' is missing required boolean field '" + path + "'"
            );
        }

        final Object value = section.get(path);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' has non-boolean field '" + path + "'"
            );
        }
        return bool;
    }

    private long requireNonNegativeLong(
            final ItemKey itemKey,
            final ConfigurationSection section,
            final String path
    ) {
        if (!section.contains(path)) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' is missing required numeric field '" + path + "'"
            );
        }

        final Object value = section.get(path);
        final long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value).trim());
            } catch (final NumberFormatException ex) {
                throw new IllegalStateException(
                        "exchange-items.yml item '" + itemKey.value()
                                + "' has invalid long field '" + path + "'",
                        ex
                );
            }
        }

        if (parsed < 0L) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' has negative field '" + path + "'"
            );
        }
        return parsed;
    }

    private BigDecimal requireNonNegativeBigDecimal(
            final ItemKey itemKey,
            final ConfigurationSection section,
            final String path
    ) {
        if (!section.contains(path)) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' is missing required decimal field '" + path + "'"
            );
        }

        final BigDecimal value = this.asBigDecimal(section.get(path));
        if (value == null) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' has null decimal field '" + path + "'"
            );
        }
        if (value.signum() < 0) {
            throw new IllegalStateException(
                    "exchange-items.yml item '" + itemKey.value()
                            + "' has negative decimal field '" + path + "'"
            );
        }
        return value;
    }

    private ConfigurationSection requireSection(
            final ConfigurationSection parent,
            final String path,
            final String message
    ) {
        final ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalStateException(message);
        }
        return section;
    }

    private BigDecimal asBigDecimal(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(String.valueOf(value).trim());
    }

    private FileConfiguration loadYaml(final String resourceName) {
        final File file = new File(this.plugin.getDataFolder(), resourceName);
        if (!file.isFile()) {
            throw new IllegalStateException(
                    "Required config file '" + resourceName + "' is missing at " + file.getAbsolutePath() + ".\n"
                            + "Run /shopadmin reload to regenerate bundled defaults, then review the file before continuing."
            );
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private String normalizeItemKey(final String rawItemKey) {
        String key = rawItemKey.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        return key;
    }
}
