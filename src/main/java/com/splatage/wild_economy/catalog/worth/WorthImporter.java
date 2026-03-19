package com.splatage.wild_economy.catalog.worth;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class WorthImporter implements WorthPriceLookup {

    private final Map<String, BigDecimal> pricesByKey;

    private WorthImporter(final Map<String, BigDecimal> pricesByKey) {
        this.pricesByKey = Map.copyOf(pricesByKey);
    }

    public static WorthImporter empty() {
        return new WorthImporter(Collections.emptyMap());
    }

    public static WorthImporter fromFile(final File worthFile) throws IOException {
        if (worthFile == null) {
            throw new IllegalArgumentException("worthFile must not be null");
        }
        if (!worthFile.exists() || !worthFile.isFile()) {
            throw new IOException("Worth file does not exist: " + worthFile.getAbsolutePath());
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(worthFile);
        final Map<String, BigDecimal> prices = new LinkedHashMap<>();

        final ConfigurationSection worthSection = yaml.getConfigurationSection("worth");
        if (worthSection != null) {
            loadSectionRecursive(worthSection, "", prices);
        }

        for (final String key : yaml.getKeys(false)) {
            final Object value = yaml.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }

            final BigDecimal price = parseDecimal(value);
            if (price == null) {
                continue;
            }

            prices.put(normalizeKey(key), price);
        }

        if (prices.isEmpty()) {
            return empty();
        }

        return new WorthImporter(prices);
    }

    @Override
    public Optional<BigDecimal> findPrice(final String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.pricesByKey.get(normalizeKey(itemKey)));
    }

    public int size() {
        return this.pricesByKey.size();
    }

    public Set<String> keys() {
        return this.pricesByKey.keySet();
    }

    private static void loadSectionRecursive(
        final ConfigurationSection section,
        final String prefix,
        final Map<String, BigDecimal> output
    ) {
        for (final String childKey : section.getKeys(false)) {
            final Object value = section.get(childKey);
            final String fullKey = prefix.isEmpty() ? childKey : prefix + "." + childKey;

            if (value instanceof ConfigurationSection childSection) {
                loadSectionRecursive(childSection, fullKey, output);
                continue;
            }

            final BigDecimal price = parseDecimal(value);
            if (price == null) {
                continue;
            }

            output.put(normalizeKey(fullKey), price);
        }
    }

    private static BigDecimal parseDecimal(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String stringValue) {
            final String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(trimmed);
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static String normalizeKey(final String rawKey) {
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        return key.replace('-', '_');
    }
}
