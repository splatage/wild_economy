package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class GeneratedCatalogImporter {

    public Map<ItemKey, ExchangeCatalogEntry> importGeneratedCatalog(final File generatedCatalogFile) {
        if (generatedCatalogFile == null) {
            throw new IllegalArgumentException("generatedCatalogFile must not be null");
        }
        if (!generatedCatalogFile.exists() || !generatedCatalogFile.isFile()) {
            return Map.of();
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(generatedCatalogFile);
        final ConfigurationSection entriesSection = yaml.getConfigurationSection("entries");
        if (entriesSection == null) {
            return Map.of();
        }

        final Map<ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>();

        for (final String rawItemKey : entriesSection.getKeys(false)) {
            final ConfigurationSection section = entriesSection.getConfigurationSection(rawItemKey);
            if (section == null) {
                continue;
            }

            final ItemKey itemKey = new ItemKey(this.normalizeItemKey(rawItemKey));
            final BigDecimal derivedValue = this.getBigDecimal(section, "derived-value");
            final BigDecimal rootValue = this.getBigDecimal(section, "root-value");
            final BigDecimal baseValue = derivedValue != null
                ? derivedValue
                : rootValue != null ? rootValue : BigDecimal.ZERO;

            final GeneratedPolicyMapping policyMapping = this.mapPolicy(
                section.getString("policy", "DISABLED")
            );

            entries.put(itemKey, new ExchangeCatalogEntry(
                itemKey,
                this.prettyDisplayName(itemKey.value()),
                this.mapCategory(section.getString("category", "MISC")),
                policyMapping.policyMode(),
                baseValue,
                baseValue,
                baseValue,
                0L,
                0L,
                List.of(),
                policyMapping.buyEnabled(),
                policyMapping.sellEnabled()
            ));
        }

        return Map.copyOf(entries);
    }

    private ItemCategory mapCategory(final String rawCategory) {
        final String normalized = rawCategory == null ? "MISC" : rawCategory.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FARMING", "FOOD" -> ItemCategory.FARMING;
            case "ORES_AND_MINERALS", "REDSTONE" -> ItemCategory.MINING;
            case "MOB_DROPS" -> ItemCategory.MOB_DROPS;
            case "WOODS", "STONE", "DECORATION" -> ItemCategory.BUILDING;
            case "NETHER", "END", "TOOLS", "COMBAT", "BREWING", "TRANSPORT", "MISC" -> ItemCategory.UTILITY;
            default -> ItemCategory.UTILITY;
        };
    }

    private GeneratedPolicyMapping mapPolicy(final String rawPolicy) {
        final String normalized = rawPolicy == null ? "DISABLED" : rawPolicy.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ALWAYS_AVAILABLE" -> new GeneratedPolicyMapping(ItemPolicyMode.UNLIMITED_BUY, true, false);
            case "EXCHANGE" -> new GeneratedPolicyMapping(ItemPolicyMode.PLAYER_STOCKED, true, true);
            case "SELL_ONLY" -> new GeneratedPolicyMapping(ItemPolicyMode.PLAYER_STOCKED, false, true);
            case "DISABLED" -> new GeneratedPolicyMapping(ItemPolicyMode.DISABLED, false, false);
            default -> new GeneratedPolicyMapping(ItemPolicyMode.DISABLED, false, false);
        };
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
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeItemKey(final String rawItemKey) {
        String key = rawItemKey.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        return key;
    }

    private String prettyDisplayName(final String itemKey) {
        final String plain = itemKey.replace("minecraft:", "");
        final String[] parts = plain.split("_");
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                builder.append(parts[i].substring(1));
            }
        }
        return builder.toString();
    }

    private record GeneratedPolicyMapping(
        ItemPolicyMode policyMode,
        boolean buyEnabled,
        boolean sellEnabled
    ) {
    }
}
