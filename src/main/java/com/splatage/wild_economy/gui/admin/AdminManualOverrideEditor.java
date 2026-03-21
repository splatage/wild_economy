package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.admin.AdminCatalogItemKeys;
import com.splatage.wild_economy.catalog.admin.AdminCatalogManualOverride;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AdminManualOverrideEditor {

    private static final String DEFAULT_GUI_NOTE = "Manual override from admin GUI.";

    private final WildEconomyPlugin plugin;

    public AdminManualOverrideEditor(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public AdminCatalogManualOverride loadOverride(final String itemKey) {
        final String canonical = AdminCatalogItemKeys.canonicalize(itemKey);
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.overrideFile());
        final ConfigurationSection overrides = yaml.getConfigurationSection("overrides");
        if (overrides == null) {
            return null;
        }
        final ConfigurationSection section = overrides.getConfigurationSection(canonical);
        if (section == null) {
            return null;
        }
        return new AdminCatalogManualOverride(
            canonical,
            parsePolicy(section.getString("policy")),
            null,
            trimToNull(section.getString("stock-profile")),
            trimToNull(section.getString("eco-envelope")),
            trimToNull(section.getString("note"))
        );
    }

    public List<String> loadStockProfileNames() {
        return this.loadTopLevelKeys("stock-profiles.yml", "stock-profiles");
    }

    public List<String> loadEcoEnvelopeNames() {
        return this.loadTopLevelKeys("eco-envelopes.yml", "eco-envelopes");
    }

    public String nextPolicy(final String currentPolicy) {
        final CatalogPolicy[] values = CatalogPolicy.values();
        CatalogPolicy current = parsePolicy(currentPolicy);
        if (current == null) {
            current = CatalogPolicy.EXCHANGE;
        }
        final int index = Arrays.asList(values).indexOf(current);
        return values[(index + 1) % values.length].name();
    }

    public String nextNamedValue(final List<String> values, final String current) {
        if (values.isEmpty()) {
            return current;
        }
        final String normalizedCurrent = trimToNull(current);
        if (normalizedCurrent == null) {
            return values.get(0);
        }
        final int currentIndex = values.indexOf(normalizedCurrent);
        if (currentIndex < 0) {
            return values.get(0);
        }
        return values.get((currentIndex + 1) % values.size());
    }

    public String nextNote(final String currentNote) {
        final List<String> options = new ArrayList<>();
        final String trimmedCurrent = trimToNull(currentNote);
        if (trimmedCurrent != null) {
            options.add(trimmedCurrent);
        }
        options.add(DEFAULT_GUI_NOTE);
        options.add("");
        final LinkedHashSet<String> unique = new LinkedHashSet<>(options);
        final List<String> deduped = new ArrayList<>(unique);
        final String current = currentNote == null ? "" : currentNote;
        final int index = deduped.indexOf(current);
        if (index < 0) {
            return deduped.get(0);
        }
        return deduped.get((index + 1) % deduped.size());
    }

    public void saveOverride(
        final String itemKey,
        final String policyName,
        final String stockProfile,
        final String ecoEnvelope,
        final String note
    ) throws IOException {
        final String canonical = AdminCatalogItemKeys.canonicalize(itemKey);
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.overrideFile());
        ConfigurationSection overrides = yaml.getConfigurationSection("overrides");
        if (overrides == null) {
            overrides = yaml.createSection("overrides");
        }
        ConfigurationSection section = overrides.getConfigurationSection(canonical);
        if (section == null) {
            section = overrides.createSection(canonical);
        }

        section.set("policy", parsePolicy(policyName).name());
        section.set("stock-profile", trimToNull(stockProfile));
        section.set("eco-envelope", trimToNull(ecoEnvelope));
        section.set("note", trimToNull(note));

        yaml.save(this.overrideFile());
    }

    public boolean removeOverride(final String itemKey) throws IOException {
        final String canonical = AdminCatalogItemKeys.canonicalize(itemKey);
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.overrideFile());
        final ConfigurationSection overrides = yaml.getConfigurationSection("overrides");
        if (overrides == null || !overrides.contains(canonical)) {
            return false;
        }
        overrides.set(canonical, null);
        yaml.save(this.overrideFile());
        return true;
    }

    public boolean stockProfileExists(final String name) {
        return name != null && this.loadStockProfileNames().contains(name);
    }

    public boolean ecoEnvelopeExists(final String name) {
        return name != null && this.loadEcoEnvelopeNames().contains(name);
    }

    private List<String> loadTopLevelKeys(final String fileName, final String rootPath) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), fileName));
        final ConfigurationSection section = yaml.getConfigurationSection(rootPath);
        if (section == null) {
            return List.of();
        }
        final Set<String> keys = new LinkedHashSet<>(section.getKeys(false));
        return List.copyOf(keys);
    }

    private File overrideFile() {
        return new File(this.plugin.getDataFolder(), "manual-overrides.yml");
    }

    private static CatalogPolicy parsePolicy(final String value) {
        if (value == null || value.isBlank()) {
            return CatalogPolicy.EXCHANGE;
        }
        try {
            return CatalogPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return CatalogPolicy.EXCHANGE;
        }
    }

    private static String trimToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
