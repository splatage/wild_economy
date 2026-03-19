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
