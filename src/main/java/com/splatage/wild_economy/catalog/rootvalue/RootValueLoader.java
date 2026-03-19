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
    private final List<WildcardRule> wildcardRules;
    private final Set<String> configuredKeys;

    private RootValueLoader(
        final Map<String, BigDecimal> exactRootValuesByKey,
        final List<WildcardRule> wildcardRules,
        final Set<String> configuredKeys
    ) {
        this.exactRootValuesByKey = Map.copyOf(exactRootValuesByKey);
        this.wildcardRules = List.copyOf(wildcardRules);
        this.configuredKeys = Set.copyOf(configuredKeys);
    }

    public static RootValueLoader empty() {
        return new RootValueLoader(Collections.emptyMap(), Collections.emptyList(), Collections.emptySet());
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
        final List<WildcardRule> wildcardRules = new ArrayList<>();
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
                wildcardRules.add(new WildcardRule(
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

        wildcardRules.sort(
            Comparator.comparingInt(WildcardRule::specificity).reversed()
                .thenComparingInt(WildcardRule::order)
        );

        return new RootValueLoader(exactValues, wildcardRules, configuredKeys);
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

        for (final WildcardRule rule : this.wildcardRules) {
            if (rule.matches(normalizedItemKey)) {
                return Optional.of(rule.value());
            }
        }

        return Optional.empty();
    }

    public int size() {
        return this.exactRootValuesByKey.size() + this.wildcardRules.size();
    }

    public Set<String> keys() {
        return this.configuredKeys;
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

    private static boolean isWildcardPattern(final String key) {
        return key.indexOf('*') >= 0;
    }

    private static int wildcardSpecificity(final String key) {
        int specificity = 0;
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) != '*') {
                specificity++;
            }
        }
        return specificity;
    }

    private static Pattern compileGlob(final String glob) {
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
