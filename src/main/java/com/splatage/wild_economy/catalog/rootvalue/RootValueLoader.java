package com.splatage.wild_economy.catalog.rootvalue;

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

public final class RootValueLoader implements RootValueLookup {

    private final Map<String, BigDecimal> rootValuesByKey;

    private RootValueLoader(final Map<String, BigDecimal> rootValuesByKey) {
        this.rootValuesByKey = Map.copyOf(rootValuesByKey);
    }

    public static RootValueLoader empty() {
        return new RootValueLoader(Collections.emptyMap());
    }

    public static RootValueLoader fromFile(final File rootValuesFile) throws IOException {
        if (rootValuesFile == null) {
            throw new IllegalArgumentException("rootValuesFile must not be null");
        }
        if (!rootValuesFile.exists() || !rootValuesFile.isFile()) {
            throw new IOException("Root values file does not exist: " + rootValuesFile.getAbsolutePath());
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(rootValuesFile);
        final ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
        if (itemsSection == null) {
            return empty();
        }

        final Map<String, BigDecimal> rootValues = new LinkedHashMap<>();
        for (final String rawKey : itemsSection.getKeys(false)) {
            final BigDecimal value = parseDecimal(itemsSection.get(rawKey));
            if (value == null) {
                continue;
            }
            rootValues.put(normalizeKey(rawKey), value);
        }

        return new RootValueLoader(rootValues);
    }

    @Override
    public Optional<BigDecimal> findRootValue(final String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.rootValuesByKey.get(normalizeKey(itemKey)));
    }

    public int size() {
        return this.rootValuesByKey.size();
    }

    public Set<String> keys() {
        return this.rootValuesByKey.keySet();
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
