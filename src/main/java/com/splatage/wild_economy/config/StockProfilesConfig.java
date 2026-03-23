package com.splatage.wild_economy.config;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class StockProfilesConfig {

    private final Map<String, StockProfileDefinition> stockProfiles;

    public StockProfilesConfig(final Map<String, StockProfileDefinition> stockProfiles) {
        this.stockProfiles = Map.copyOf(stockProfiles);
    }

    public Map<String, StockProfileDefinition> stockProfiles() {
        return this.stockProfiles;
    }

    public Optional<StockProfileDefinition> get(final String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.stockProfiles.get(normalizeKey(key)));
    }

    public static String normalizeKey(final String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }

    public record StockProfileDefinition(
        long stockCap,
        long turnoverAmountPerInterval
    ) {
    }
}
