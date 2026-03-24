package com.splatage.wild_economy.catalog.rootvalue;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
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

public final class RootValueLoader implements RootValueLookup, CategoryHintLookup {

    private final Map<String, BigDecimal> exactRootValuesByKey;
    private final List<WildcardRule<BigDecimal>> rootValueWildcardRules;
    private final Map<String, CatalogCategory> exactCategoryHintsByKey;
    private final List<WildcardRule<CatalogCategory>> categoryWildcardRules;
    private final Set<String> configuredKeys;

    private RootValueLoader(
        final Map<String, BigDecimal> exactRootValuesByKey,
        final List<WildcardRule<BigDecimal>> rootValueWildcardRules,
        final Map<String, CatalogCategory> exactCategoryHintsByKey,
        final List<WildcardRule<CatalogCategory>> categoryWildcardRules,
        final Set<String> configuredKeys
    ) {
        this.exactRootValuesByKey = Map.copyOf(exactRootValuesByKey);
        this.rootValueWildcardRules = List.copyOf(rootValueWildcardRules);
        this.exactCategoryHintsByKey = Map.copyOf(exactCategoryHintsByKey);
        this.categoryWildcardRules = List.copyOf(categoryWildcardRules);
        this.configuredKeys = Set.copyOf(configuredKeys);
    }

    public static RootValueLoader empty() {
        return new RootValueLoader(
            Collections.emptyMap(),
            Collections.emptyList(),
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

        final CategoryHints categoryHints = loadCategoryHints(yaml);

        return new RootValueLoader(
            exactValues,
            valueWildcardRules,
            categoryHints.exactCategoryHintsByKey(),
            categoryHints.categoryWildcardRules(),
            configuredKeys
        );
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

    @Override
    public Optional<CatalogCategory> findCategoryHint(final String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return Optional.empty();
        }

        final String normalizedItemKey = normalizeKey(itemKey);

        final CatalogCategory exactCategory = this.exactCategoryHintsByKey.get(normalizedItemKey);
        if (exactCategory != null) {
            return Optional.of(exactCategory);
        }

        for (final WildcardRule<CatalogCategory> rule : this.categoryWildcardRules) {
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

    private static CategoryHints loadCategoryHints(final YamlConfiguration yaml) {
        final ConfigurationSection groupsSection = yaml.getConfigurationSection("layout.groups");
        if (groupsSection == null) {
            return new CategoryHints(Collections.emptyMap(), Collections.emptyList());
        }

        final Map<String, CatalogCategory> exactCategoryHints = new LinkedHashMap<>();
        final List<WildcardRule<CatalogCategory>> wildcardRules = new ArrayList<>();

        int order = 0;
        for (final String groupId : groupsSection.getKeys(false)) {
            final ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupId);
            if (groupSection == null) {
                continue;
            }

            final CatalogCategory category = parseCategory(
                groupSection.getString("generated-category", groupSection.getString("category", null))
            );
            if (category == null) {
                continue;
            }

            for (final String rawKey : groupSection.getStringList("item-keys")) {
                final String normalizedKey = normalizeKey(rawKey);
                if (normalizedKey.isBlank()) {
                    continue;
                }
                if (isWildcardPattern(normalizedKey)) {
                    wildcardRules.add(new WildcardRule<>(
                        normalizedKey,
                        compileGlob(normalizedKey),
                        category,
                        wildcardSpecificity(normalizedKey),
                        order++
                    ));
                    continue;
                }
                exactCategoryHints.putIfAbsent(normalizedKey, category);
                order++;
            }

            for (final String rawPattern : groupSection.getStringList("item-key-patterns")) {
                final String normalizedPattern = normalizeKey(rawPattern);
                if (normalizedPattern.isBlank()) {
                    continue;
                }
                wildcardRules.add(new WildcardRule<>(
                    normalizedPattern,
                    compileGlob(normalizedPattern),
                    category,
                    wildcardSpecificity(normalizedPattern),
                    order++
                ));
            }
        }

        wildcardRules.sort(
            Comparator.comparingInt(WildcardRule<CatalogCategory>::specificity).reversed()
                .thenComparingInt(WildcardRule<CatalogCategory>::order)
        );

        return new CategoryHints(exactCategoryHints, wildcardRules);
    }

    private static CatalogCategory parseCategory(final String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return null;
        }

        final String normalized = rawCategory.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return CatalogCategory.valueOf(normalized);
        } catch (final IllegalArgumentException ignored) {
            return null;
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

    private record CategoryHints(
        Map<String, CatalogCategory> exactCategoryHintsByKey,
        List<WildcardRule<CatalogCategory>> categoryWildcardRules
    ) {
    }

    private record WildcardRule<T>(
        String pattern,
        Pattern regex,
        T value,
        int specificity,
        int order
    ) {
        private boolean matches(final String itemKey) {
            return this.regex.matcher(itemKey).matches();
        }
    }
}
