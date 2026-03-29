package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AdminCatalogPolicyProfileLoader {

    private AdminCatalogPolicyProfileLoader() {
    }

    public static Map<CatalogPolicy, AdminCatalogPolicyProfile> load(final File file) throws IOException {
        Objects.requireNonNull(file, "file");
        requireFile(file);
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection section = yaml.getConfigurationSection("policies");
        final Map<CatalogPolicy, AdminCatalogPolicyProfile> profiles = new EnumMap<>(CatalogPolicy.class);
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final ConfigurationSection policySection = section.getConfigurationSection(key);
                final CatalogPolicy policy = parsePolicy(key);
                if (policySection == null || policy == null) {
                    continue;
                }
                profiles.put(policy, fromSection(key, policy, policySection));
            }
        }
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            profiles.putIfAbsent(policy, defaultProfile(policy));
        }
        return profiles;
    }

    public static AdminCatalogPolicyProfile defaultProfile(final CatalogPolicy policy) {
        return switch (policy) {
            case ALWAYS_AVAILABLE -> new AdminCatalogPolicyProfile(
                policy.name(),
                policy,
                "UNLIMITED_BUY",
                true,
                false,
                false,
                true,
                false,
                "unlimited_buy_utility",
                "world_damage_unlimited_buy",
                "Unlimited buy from server; no player-stock requirement."
            );
            case EXCHANGE -> new AdminCatalogPolicyProfile(
                policy.name(),
                policy,
                "PLAYER_STOCKED",
                true,
                true,
                true,
                false,
                true,
                "exchange_default",
                "exchange_default",
                "Player-stocked buy and sell."
            );
            case SELL_ONLY -> new AdminCatalogPolicyProfile(
                policy.name(),
                policy,
                "PLAYER_STOCKED",
                false,
                true,
                false,
                false,
                false,
                "sell_only_cleanup",
                "sell_only_cleanup",
                "Players can sell; no public buy path."
            );
            case DISABLED -> new AdminCatalogPolicyProfile(
                policy.name(),
                policy,
                "DISABLED",
                false,
                false,
                false,
                false,
                false,
                "disabled_placeholder",
                "disabled_placeholder",
                "No buy or sell."
            );
        };
    }

    private static AdminCatalogPolicyProfile fromSection(
        final String id,
        final CatalogPolicy policy,
        final ConfigurationSection section
    ) {
        final AdminCatalogPolicyProfile fallback = defaultProfile(policy);
        return new AdminCatalogPolicyProfile(
            id,
            policy,
            section.getString("runtime-policy", fallback.runtimePolicy()),
            section.getBoolean("buy-enabled", fallback.buyEnabled()),
            section.getBoolean("sell-enabled", fallback.sellEnabled()),
            section.getBoolean("stock-backed", fallback.stockBacked()),
            section.getBoolean("unlimited-buy", fallback.unlimitedBuy()),
            section.getBoolean("requires-player-stock-to-buy", fallback.requiresPlayerStockToBuy()),
            section.getString("default-stock-profile", fallback.defaultStockProfile()),
            section.getString("default-eco-envelope", fallback.defaultEcoEnvelope()),
            section.getString("description", fallback.description())
        );
    }

    private static CatalogPolicy parsePolicy(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CatalogPolicy.valueOf(value.trim().toUpperCase());
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void requireFile(final File file) throws IOException {
        if (file.isFile()) {
            return;
        }
        throw new IOException(
            "Required config file 'policy-profiles.yml' is missing at "
                + file.getAbsolutePath()
                + ". Run /shopadmin reload to regenerate bundled defaults, then review the file before rerunning this action."
        );
    }
}
