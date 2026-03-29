package com.splatage.wild_economy.testing;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public record HarnessConfig(
        boolean enabled,
        boolean allowReset,
        String requiredPrefixMarker,
        TestProfile defaultProfile,
        Map<TestProfile, HarnessProfileSettings> profiles
) {
    public HarnessConfig {
        Objects.requireNonNull(requiredPrefixMarker, "requiredPrefixMarker");
        Objects.requireNonNull(defaultProfile, "defaultProfile");
        profiles = Map.copyOf(Objects.requireNonNull(profiles, "profiles"));
        if (!profiles.containsKey(defaultProfile)) {
            throw new IllegalArgumentException("Default profile '" + defaultProfile + "' is not configured");
        }
    }

    public static HarnessConfig load(final File file) {
        final FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        final String marker = configuration.getString("required-prefix-marker", "test_");
        final TestProfile defaultProfile = TestProfile.parse(configuration.getString("default-profile", "smoke"));
        final ConfigurationSection profilesSection = configuration.getConfigurationSection("profiles");
        if (profilesSection == null) {
            throw new IllegalStateException("Harness config is missing the 'profiles' section: " + file.getAbsolutePath());
        }

        final Map<TestProfile, HarnessProfileSettings> profiles = new EnumMap<>(TestProfile.class);
        for (final String key : profilesSection.getKeys(false)) {
            final ConfigurationSection section = profilesSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            final TestProfile profile = TestProfile.parse(key);
            profiles.put(profile, new HarnessProfileSettings(
                    section.getInt("player-count"),
                    section.getLong("random-seed"),
                    section.getInt("exchange-transaction-count", 0),
                    section.getInt("store-purchase-count", 0),
                    section.getInt("entitlement-grant-count", 0)
            ));
        }

        return new HarnessConfig(
                configuration.getBoolean("enabled", false),
                configuration.getBoolean("allow-reset", false),
                marker,
                defaultProfile,
                profiles
        );
    }

    public HarnessProfileSettings profile(final TestProfile profile) {
        final HarnessProfileSettings settings = this.profiles.get(profile);
        if (settings == null) {
            throw new IllegalStateException("Harness profile '" + profile + "' is not configured");
        }
        return settings;
    }
}
