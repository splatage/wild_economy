package com.splatage.wild_economy.gui.layout;

import com.splatage.wild_economy.catalog.rootvalue.RootValueLoader;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LayoutBlueprintLoader {

    public LayoutBlueprint load(final File layoutFile) throws IOException {
        if (layoutFile == null) {
            throw new IllegalArgumentException("layoutFile must not be null");
        }
        if (!layoutFile.exists() || !layoutFile.isFile()) {
            throw new IOException("Layout file does not exist: " + layoutFile.getAbsolutePath());
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(layoutFile);
        final ConfigurationSection groupsSection = yaml.getConfigurationSection("layout.groups");
        final ConfigurationSection overridesSection = yaml.getConfigurationSection("layout.overrides");

        final Map<String, LayoutGroupDefinition> groups = new LinkedHashMap<>();
        if (groupsSection != null) {
            int defaultOrder = 0;
            for (final String rawGroupKey : groupsSection.getKeys(false)) {
                final ConfigurationSection groupSection = groupsSection.getConfigurationSection(rawGroupKey);
                if (groupSection == null) {
                    continue;
                }
                final String groupKey = normalizeKey(rawGroupKey);
                final String groupLabel = groupSection.getString("label", rawGroupKey);
                final int groupOrder = groupSection.getInt("order", defaultOrder++);
                final String groupIcon = blankToNull(groupSection.getString("icon", null));
                final Map<String, LayoutChildDefinition> children = new LinkedHashMap<>();
                final ConfigurationSection childrenSection = groupSection.getConfigurationSection("children");
                if (childrenSection != null) {
                    int defaultChildOrder = 0;
                    for (final String rawChildKey : childrenSection.getKeys(false)) {
                        final ConfigurationSection childSection = childrenSection.getConfigurationSection(rawChildKey);
                        if (childSection == null) {
                            continue;
                        }
                        final String childKey = normalizeKey(rawChildKey);
                        children.put(childKey, new LayoutChildDefinition(
                            childKey,
                            childSection.getString("label", rawChildKey),
                            childSection.getInt("order", defaultChildOrder++),
                            blankToNull(childSection.getString("icon", null)),
                            canonicalizeAll(childSection.getStringList("item-keys")),
                            canonicalizeAll(childSection.getStringList("item-key-patterns"))
                        ));
                    }
                }
                groups.put(groupKey, new LayoutGroupDefinition(
                    groupKey,
                    groupLabel,
                    groupOrder,
                    groupIcon,
                    children,
                    canonicalizeAll(groupSection.getStringList("item-keys")),
                    canonicalizeAll(groupSection.getStringList("item-key-patterns"))
                ));
            }
        }

        final Map<String, LayoutOverride> overrides = new LinkedHashMap<>();
        if (overridesSection != null) {
            for (final String rawItemKey : overridesSection.getKeys(false)) {
                final ConfigurationSection overrideSection = overridesSection.getConfigurationSection(rawItemKey);
                if (overrideSection == null) {
                    continue;
                }
                final String itemKey = RootValueLoader.normalizeKey(rawItemKey);
                final String group = normalizeKey(overrideSection.getString("group", null));
                final String child = normalizeKey(overrideSection.getString("child", null));
                overrides.put(itemKey, new LayoutOverride(itemKey, blankToNull(group), blankToNull(child)));
            }
        }

        return new LayoutBlueprint(groups, overrides);
    }

    private static List<String> canonicalizeAll(final List<String> values) {
        return values.stream()
            .map(RootValueLoader::normalizeKey)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private static String normalizeKey(final String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
