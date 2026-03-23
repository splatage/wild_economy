package com.splatage.wild_economy.config;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class EcoEnvelopesConfig {

    private final Map<String, EcoEnvelopeDefinition> ecoEnvelopes;

    public EcoEnvelopesConfig(final Map<String, EcoEnvelopeDefinition> ecoEnvelopes) {
        this.ecoEnvelopes = Map.copyOf(ecoEnvelopes);
    }

    public Map<String, EcoEnvelopeDefinition> ecoEnvelopes() {
        return this.ecoEnvelopes;
    }

    public Optional<EcoEnvelopeDefinition> get(final String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.ecoEnvelopes.get(normalizeKey(key)));
    }

    public static String normalizeKey(final String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }

    public record EcoEnvelopeDefinition(
        BigDecimal buyPriceMultiplier,
        BigDecimal sellPriceMultiplier,
        long minStock,
        long maxStock,
        BigDecimal floorPriceFactor
    ) {
    }
}
