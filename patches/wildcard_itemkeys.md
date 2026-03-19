# wild_economy Root Value Wildcard Patch Files

These are complete repo-aligned replacement files to add wildcard su([raw.githubusercontent.com](https://raw.githubusercontent.com/splatage/wild_economy/main/src/main/java/com/splatage/wild_economy/catalog/rootvalue/RootValueLoader.java))n
Supported examples:

* `minecraft:*_sapling`
* `minecraft:*_log`
* `*_sapling`
* `oak_*`

Behavior:

* exact match wins over wildcard
* among wildcard rules, more specific patterns win
* generator-side loader resolves wildcard anchors at lookup time
* runtime-side importer expands wildcard patterns into exact `ItemKey` entries so existing runtime merge code continues to work

---

## File: `src/main/java/com/splatage/wild_economy/catalog/rootvalue/RootValueLoader.java`

```java
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
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/RootValueImporter.java`

```java
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemKey;
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

        final Map<ItemKey, BigDecimal> exactValues = new LinkedHashMap<>();
        final List<WildcardRule> wildcardRules = new ArrayList<>();

        int order = 0;
        for (final String rawKey : itemsSection.getKeys(false)) {
            final Object value = itemsSection.get(rawKey);
            if (!(value instanceof Number) && !(value instanceof String)) {
                continue;
            }

            final BigDecimal amount = this.asBigDecimal(value);
            if (amount == null) {
                continue;
            }

            final String normalizedKey = this.normalizeItemKey(rawKey);
            if (this.isWildcardPattern(normalizedKey)) {
                wildcardRules.add(new WildcardRule(
                    normalizedKey,
                    this.compileGlob(normalizedKey),
                    amount,
                    this.wildcardSpecificity(normalizedKey),
                    order++
                ));
                continue;
            }

            exactValues.put(new ItemKey(normalizedKey), amount);
            order++;
        }

        final Map<ItemKey, BigDecimal> expanded = new LinkedHashMap<>();

        wildcardRules.sort(
            Comparator.comparingInt(WildcardRule::specificity)
                .thenComparingInt(WildcardRule::order)
        );

        for (final WildcardRule rule : wildcardRules) {
            for (final Material material : Material.values()) {
                if (!this.isIncludedMaterial(material)) {
                    continue;
                }

                final String itemKey = this.normalizeItemKey(material);
                if (rule.matches(itemKey)) {
                    expanded.put(new ItemKey(itemKey), rule.value());
                }
            }
        }

        expanded.putAll(exactValues);
        return Map.copyOf(expanded);
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

    private boolean isWildcardPattern(final String key) {
        return key.indexOf('*') >= 0;
    }

    private int wildcardSpecificity(final String key) {
        int specificity = 0;
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) != '*') {
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

    private String normalizeItemKey(final String rawKey) {
        String lowered = rawKey.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!lowered.contains(":")) {
            lowered = "minecraft:" + lowered;
        }
        return lowered;
    }

    private String normalizeItemKey(final Material material) {
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
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
```

---

## Example `root-values.yml`

```yaml
items:
  minecraft:*_sapling: 6.0
  minecraft:*_log: 12.0
  minecraft:jungle_sapling: 7.5
  minecraft:oak_log: 10.0
```

Expected behavior:

* `minecraft:jungle_sapling` -> `7.5` (exact beats wildcard)
* `minecraft:birch_sapling` -> `6.0`
* `minecraft:oak_log` -> `10.0` (exact beats wildcard)
* `minecraft:jungle_log` -> `12.0`

---

## Notes

This patch intentionally keeps wildcard support simple:

* `*` is the only wildcard
* no regex syntax in config
* exact keys still win last
* runtime importer expands wildcard rules into exact `ItemKey` values so existing runtime merge logic does not need to change
