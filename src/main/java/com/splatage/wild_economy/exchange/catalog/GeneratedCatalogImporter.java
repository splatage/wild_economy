package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
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
            final GeneratedItemCategory generatedCategory = this.mapGeneratedCategory(
                section.getString("category", "MISC")
            );
            final ItemCategory topLevelCategory = this.mapTopLevelCategory(generatedCategory);

            final BigDecimal derivedValue = this.getBigDecimal(section, "derived-value");
            final BigDecimal rootValue = this.getBigDecimal(section, "root-value");
            final BigDecimal baseValue = derivedValue != null
                ? derivedValue
                : rootValue != null ? rootValue : BigDecimal.ZERO;

            final GeneratedPolicyMapping policyMapping = this.mapPolicy(section.getString("policy", "DISABLED"));

            entries.put(itemKey, new ExchangeCatalogEntry(
                itemKey,
                this.prettyDisplayName(itemKey.value()),
                topLevelCategory,
                generatedCategory,
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

    private GeneratedItemCategory mapGeneratedCategory(final String rawCategory) {
        final String normalized = rawCategory == null ? "MISC" : rawCategory.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FARMING" -> GeneratedItemCategory.FARMING;
            case "FOOD" -> GeneratedItemCategory.FOOD;
            case "ORES_AND_MINERALS" -> GeneratedItemCategory.ORES_AND_MINERALS;
            case "MOB_DROPS" -> GeneratedItemCategory.MOB_DROPS;
            case "WOODS" -> GeneratedItemCategory.WOODS;
            case "STONE" -> GeneratedItemCategory.STONE;
            case "REDSTONE" -> GeneratedItemCategory.REDSTONE;
            case "TOOLS" -> GeneratedItemCategory.TOOLS;
            case "BREWING" -> GeneratedItemCategory.BREWING;
            case "TRANSPORT" -> GeneratedItemCategory.TRANSPORT;
            case "COMBAT" -> GeneratedItemCategory.COMBAT;
            case "NETHER" -> GeneratedItemCategory.NETHER;
            case "END" -> GeneratedItemCategory.END;
            case "DECORATION" -> GeneratedItemCategory.DECORATION;
            case "MISC" -> GeneratedItemCategory.MISC;
            default -> GeneratedItemCategory.MISC;
        };
    }

    private ItemCategory mapTopLevelCategory(final GeneratedItemCategory generatedCategory) {
        return switch (generatedCategory) {
            case FARMING, FOOD -> ItemCategory.FARMING_AND_FOOD;
            case ORES_AND_MINERALS -> ItemCategory.MINING_AND_MINERALS;
            case MOB_DROPS -> ItemCategory.MOB_DROPS;
            case WOODS, STONE -> ItemCategory.BUILDING_MATERIALS;
            case REDSTONE, TOOLS, BREWING, TRANSPORT -> ItemCategory.REDSTONE_AND_UTILITY;
            case COMBAT, NETHER, END -> ItemCategory.COMBAT_AND_ADVENTURE;
            case DECORATION, MISC -> ItemCategory.MISC;
        };
    }

    private GeneratedPolicyMapping mapPolicy(final String rawPolicy) {
        final String normalized = rawPolicy == null ? "DISABLED" : rawPolicy.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ALWAYS_AVAILABLE" -> new GeneratedPolicyMapping(ItemPolicyMode.UNLIMITED_BUY, true, true);
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
        for (final String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
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
