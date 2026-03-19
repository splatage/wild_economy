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
