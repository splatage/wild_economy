package com.splatage.wild_economy.catalog.rootvalue;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class RootValueLoader implements RootValueLookup {

    private final Map<String, BigDecimal> exactRootValuesByKey;
    private final List<WildcardRule<BigDecimal>> rootValueWildcardRules;
    private final Set<String> configuredKeys;

    private RootValueLoader(
        final Map<String, BigDecimal> exactRootValuesByKey,
        final List<WildcardRule<BigDecimal>> rootValueWildcardRules,
        final Set<String> configuredKeys
    ) {
        this.exactRootValuesByKey = Map.copyOf(exactRootValuesByKey);
        this.rootValueWildcardRules = List.copyOf(rootValueWildcardRules);
        this.configuredKeys = Set.copyOf(configuredKeys);
    }

    public static RootValueLoader empty() {
        return new RootValueLoader(
            Collections.emptyMap(),
            Collections.emptyList(),
            Collections.emptySet()
        );
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

        final Map<String, BigDecimal> exactValues = new LinkedHashMap<>();
        final List<WildcardRule<BigDecimal>> valueWildcardRules = new ArrayList<>();
        final Set<String> configuredKeys = new LinkedHashSet<>();

        int order = 0;
        for (final String rawKey : itemsSection.getKeys(false)) {
            final BigDecimal value = parseDecimal(itemsSection.get(rawKey));
            if (value == null) {
                continue;
            }

            final String normalizedKey = normalizeKey(rawKey);
            configuredKeys.add(normalizedKey);

            if (isWildcardPattern(normalizedKey)) {
                valueWildcardRules.add(new WildcardRule<>(
                    normalizedKey,
                    compileGlob(normalizedKey),
                    value,
                    wildcardSpecificity(normalizedKey),
                    order++
                ));
                continue;
            }

            exactValues.put(normalizedKey, value);
            order++;
        }

        valueWildcardRules.sort(
            Comparator.comparingInt(WildcardRule<BigDecimal>::specificity).reversed()
                .thenComparingInt(WildcardRule<BigDecimal>::order)
        );

        return new RootValueLoader(exactValues, valueWildcardRules, configuredKeys);
    }

    @Override
    public Optional<BigDecimal> findRootValue(final String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return Optional.empty();
        }

        final String normalizedItemKey = normalizeKey(itemKey);

        final BigDecimal exactValue = this.exactRootValuesByKey.get(normalizedItemKey);
        if (exactValue != null) {
            return Optional.of(exactValue);
        }

        for (final WildcardRule<BigDecimal> rule : this.rootValueWildcardRules) {
            if (rule.matches(normalizedItemKey)) {
                return Optional.of(rule.value());
            }
        }

        return Optional.empty();
    }

    public int size() {
        return this.exactRootValuesByKey.size() + this.rootValueWildcardRules.size();
    }

    public Set<String> keys() {
        return this.configuredKeys;
    }

    private static BigDecimal parseDecimal(final Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (rawValue instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        final String text = rawValue.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isWildcardPattern(final String key) {
        return key.indexOf('*') >= 0 || key.indexOf('?') >= 0;
    }

    private static int wildcardSpecificity(final String pattern) {
        int specificity = 0;
        for (int i = 0; i < pattern.length(); i++) {
            final char character = pattern.charAt(i);
            if (character != '*' && character != '?') {
                specificity++;
            }
        }
        return specificity;
    }

    public static String normalizeKey(final String rawKey) {
        if (rawKey == null) {
            return "";
        }
        String normalized = rawKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized;
    }

    private static Pattern compileGlob(final String normalizedPattern) {
        final StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < normalizedPattern.length(); i++) {
            final char character = normalizedPattern.charAt(i);
            switch (character) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' -> regex.append('\\').append(character);
                default -> regex.append(character);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    private record WildcardRule<T>(String normalizedPattern, Pattern regex, T value, int specificity, int order) {
        private boolean matches(final String normalizedItemKey) {
            return this.regex.matcher(normalizedItemKey).matches();
        }
    }
}
