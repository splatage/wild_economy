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
