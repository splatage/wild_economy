package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.admin.AdminCatalogItemKeys;
import com.splatage.wild_economy.catalog.admin.AdminCatalogManualOverride;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPolicyProfile;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPolicyProfileLoader;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
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

    public Map<CatalogPolicy, AdminCatalogPolicyProfile> loadPolicyProfiles() {
        try {
            return AdminCatalogPolicyProfileLoader.load(this.policyProfileFile());
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to load policy profiles", exception);
            final Map<CatalogPolicy, AdminCatalogPolicyProfile> fallback = new java.util.EnumMap<>(CatalogPolicy.class);
            for (final CatalogPolicy policy : CatalogPolicy.values()) {
                fallback.put(policy, AdminCatalogPolicyProfileLoader.defaultProfile(policy));
            }
            return fallback;
        }
    }

    public List<String> loadPolicyProfileIds() {
        final Map<CatalogPolicy, AdminCatalogPolicyProfile> profiles = this.loadPolicyProfiles();
        final List<String> ids = new ArrayList<>();
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            final AdminCatalogPolicyProfile profile = profiles.get(policy);
            if (profile != null) {
                ids.add(profile.id());
            }
        }
        return ids;
    }

    public String policyBehaviorSummary(final String policyName) {
        final CatalogPolicy policy = parsePolicy(policyName);
        if (policy == null) {
            return "Unknown policy.";
        }
        final AdminCatalogPolicyProfile profile = this.loadPolicyProfiles().get(policy);
        if (profile == null) {
            return "Unknown policy.";
        }
        return profile.description()
            + " Runtime=" + profile.runtimePolicy()
            + ", buy=" + profile.buyEnabled()
            + ", sell=" + profile.sellEnabled()
            + ", stock-backed=" + profile.stockBacked()
            + ", unlimited-buy=" + profile.unlimitedBuy();
    }

    public List<String> loadStockProfileNames() {
        return this.loadTopLevelKeys("stock-profiles.yml", "stock-profiles");
    }

    public List<String> loadEcoEnvelopeNames() {
        return this.loadTopLevelKeys("eco-envelopes.yml", "eco-envelopes");
    }

    public String nextPolicy(final String currentPolicy) {
        final List<String> ids = this.loadPolicyProfileIds();
        if (ids.isEmpty()) {
            return CatalogPolicy.EXCHANGE.name();
        }
        final String canonicalCurrent = parsePolicy(currentPolicy) == null
            ? ids.get(0)
            : parsePolicy(currentPolicy).name();
        final int index = ids.indexOf(canonicalCurrent);
        if (index < 0) {
            return ids.get(0);
        }
        return ids.get((index + 1) % ids.size());
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

        final CatalogPolicy policy = parsePolicy(policyName);
        section.set("policy", (policy == null ? CatalogPolicy.EXCHANGE : policy).name());
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

    private File policyProfileFile() {
        return new File(this.plugin.getDataFolder(), "policy-profiles.yml");
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

