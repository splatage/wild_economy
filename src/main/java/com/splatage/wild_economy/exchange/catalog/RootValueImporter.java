package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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

        final Map<ItemKey, BigDecimal> rootValues = new LinkedHashMap<>();

        for (final String rawKey : itemsSection.getKeys(false)) {
            final Object value = itemsSection.get(rawKey);
            if (!(value instanceof Number) && !(value instanceof String)) {
                continue;
            }

            final BigDecimal amount = this.asBigDecimal(value);
            if (amount == null) {
                continue;
            }

            rootValues.put(new ItemKey(this.normalizeItemKey(rawKey)), amount);
        }

        return Map.copyOf(rootValues);
    }

    private String normalizeItemKey(final String rawKey) {
        final String lowered = rawKey.toLowerCase(Locale.ROOT);
        if (lowered.contains(":")) {
            return lowered;
        }
        return "minecraft:" + lowered;
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
