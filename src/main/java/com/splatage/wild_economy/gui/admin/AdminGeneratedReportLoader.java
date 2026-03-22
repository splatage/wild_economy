package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogReviewBucket;
import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AdminGeneratedReportLoader {

    public List<AdminCatalogRuleImpact> loadRuleImpacts(final File file) {
        if (!file.isFile()) {
            return List.of();
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection rulesSection = yaml.getConfigurationSection("rules");
        if (rulesSection == null) {
            return List.of();
        }

        final List<AdminCatalogRuleImpact> ruleImpacts = new ArrayList<>();
        for (final String ruleId : rulesSection.getKeys(false)) {
            final ConfigurationSection section = rulesSection.getConfigurationSection(ruleId);
            if (section == null) {
                continue;
            }
            ruleImpacts.add(
                new AdminCatalogRuleImpact(
                    ruleId,
                    section.getBoolean("fallback-rule"),
                    section.getBoolean("has-match-criteria"),
                    section.getInt("match-count"),
                    section.getInt("win-count"),
                    section.getInt("loss-count"),
                    this.loadPolicyCounts(section.getConfigurationSection("winning-policies")),
                    this.loadPolicyCounts(section.getConfigurationSection("lost-to-policies")),
                    this.loadStringCounts(section.getConfigurationSection("lost-to-rules")),
                    section.getStringList("sample-matched-items"),
                    section.getStringList("sample-winning-items"),
                    section.getStringList("sample-lost-items")
                )
            );
        }
        return ruleImpacts;
    }

    public List<AdminCatalogReviewBucket> loadReviewBuckets(final File file) {
        if (!file.isFile()) {
            return List.of();
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection bucketsSection = yaml.getConfigurationSection("buckets");
        if (bucketsSection == null) {
            return List.of();
        }

        final List<AdminCatalogReviewBucket> reviewBuckets = new ArrayList<>();
        for (final String bucketId : bucketsSection.getKeys(false)) {
            final ConfigurationSection section = bucketsSection.getConfigurationSection(bucketId);
            if (section == null) {
                continue;
            }
            reviewBuckets.add(
                new AdminCatalogReviewBucket(
                    bucketId,
                    section.getString("description", ""),
                    section.getInt("count"),
                    section.getStringList("sample-items"),
                    this.loadStringCounts(section.getConfigurationSection("subgroup-counts")),
                    this.loadSampleMap(section.getConfigurationSection("subgroup-sample-items"))
                )
            );
        }

        reviewBuckets.sort(Comparator.comparingInt(AdminCatalogReviewBucket::count).reversed());
        return reviewBuckets;
    }

    private Map<CatalogPolicy, Integer> loadPolicyCounts(final ConfigurationSection section) {
        final Map<CatalogPolicy, Integer> counts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            counts.put(policy, 0);
        }
        if (section == null) {
            return counts;
        }

        for (final String key : section.getKeys(false)) {
            try {
                counts.put(CatalogPolicy.valueOf(key.toUpperCase(Locale.ROOT)), section.getInt(key));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        return counts;
    }

    private Map<String, Integer> loadStringCounts(final ConfigurationSection section) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        if (section == null) {
            return counts;
        }
        for (final String key : section.getKeys(false)) {
            counts.put(key, section.getInt(key));
        }
        return counts;
    }

    private Map<String, List<String>> loadSampleMap(final ConfigurationSection section) {
        final Map<String, List<String>> sampleMap = new LinkedHashMap<>();
        if (section == null) {
            return sampleMap;
        }
        for (final String key : section.getKeys(false)) {
            sampleMap.put(key, List.copyOf(section.getStringList(key)));
        }
        return sampleMap;
    }
}

