package com.splatage.wild_economy.config;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConfigValidator {

    private final ExchangeItemsConfig exchangeItemsConfig;
    private final EcoEnvelopesConfig ecoEnvelopesConfig;
    private final StockProfilesConfig stockProfilesConfig;
    private final ExchangeCatalog exchangeCatalog;

    public ConfigValidator(
            final ExchangeItemsConfig exchangeItemsConfig,
            final EcoEnvelopesConfig ecoEnvelopesConfig,
            final StockProfilesConfig stockProfilesConfig,
            final ExchangeCatalog exchangeCatalog
    ) {
        this.exchangeItemsConfig = Objects.requireNonNull(exchangeItemsConfig, "exchangeItemsConfig");
        this.ecoEnvelopesConfig = Objects.requireNonNull(ecoEnvelopesConfig, "ecoEnvelopesConfig");
        this.stockProfilesConfig = Objects.requireNonNull(stockProfilesConfig, "stockProfilesConfig");
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
    }

    public void validate() {
        final List<String> errors = new ArrayList<>();
        this.validateEcoEnvelopes(errors);
        this.validateExchangeItemOverrides(errors);
        this.validateMergedCatalog(errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException(this.buildMessage(errors));
        }
    }

    private void validateEcoEnvelopes(final List<String> errors) {
        for (final Map.Entry<String, EcoEnvelopesConfig.EcoEnvelopeDefinition> entry : this.ecoEnvelopesConfig.ecoEnvelopes().entrySet()) {
            final String key = entry.getKey();
            final EcoEnvelopesConfig.EcoEnvelopeDefinition definition = entry.getValue();
            final String prefix = "eco-envelopes." + key;

            this.requireNonNegative(errors, prefix + ".buy-price-multiplier", definition.buyPriceMultiplier());
            this.requireNonNegative(errors, prefix + ".sell-price-multiplier", definition.sellPriceMultiplier());
            this.requireNonNegative(errors, prefix + ".floor-price-factor", definition.floorPriceFactor());

            if (definition.floorPriceFactor() != null && definition.floorPriceFactor().compareTo(BigDecimal.ONE) > 0) {
                errors.add(prefix + ".floor-price-factor must be <= 1.00");
            }
            if (definition.minStock() < 0L) {
                errors.add(prefix + ".min-stock must be >= 0");
            }
            if (definition.maxStock() < definition.minStock()) {
                errors.add(prefix + ".max-stock must be >= min-stock");
            }
        }
    }

    private void validateExchangeItemOverrides(final List<String> errors) {
        for (final Map.Entry<ItemKey, ExchangeItemsConfig.RawItemEntry> entry : this.exchangeItemsConfig.items().entrySet()) {
            final ItemKey itemKey = entry.getKey();
            final ExchangeItemsConfig.RawItemEntry rawItem = entry.getValue();
            final String prefix = "exchange-items.items." + itemKey.value();

            if (rawItem.ecoEnvelopeKey() != null && this.ecoEnvelopesConfig.get(rawItem.ecoEnvelopeKey()).isEmpty()) {
                errors.add(prefix + ".eco-envelope references missing key '" + rawItem.ecoEnvelopeKey() + "'");
            }

            this.requireNonNegative(errors, prefix + ".buy-price", rawItem.buyPrice());
            this.requireNonNegative(errors, prefix + ".sell-price", rawItem.sellPrice());

            if (rawItem.buyPrice() != null
                    && rawItem.sellPrice() != null
                    && rawItem.sellPrice().compareTo(rawItem.buyPrice()) > 0) {
                errors.add(prefix + " has sell-price greater than buy-price");
            }
        }
    }

    private void validateMergedCatalog(final List<String> errors) {
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.allEntries()) {
            final String prefix = "catalog." + entry.itemKey().value();

            this.requireNonNegative(errors, prefix + ".buyPrice", entry.buyPrice());
            this.requireNonNegative(errors, prefix + ".sellPrice", entry.sellPrice());

            if (entry.stockCap() < 0L) {
                errors.add(prefix + ".stockCap must be >= 0");
            }
            if (entry.turnoverAmountPerInterval() < 0L) {
                errors.add(prefix + ".turnoverAmountPerInterval must be >= 0");
            }
            if (entry.buyEnabled()
                    && entry.sellEnabled()
                    && entry.sellPrice().compareTo(entry.buyPrice()) > 0) {
                errors.add(prefix + " has arbitrage risk: sellPrice is greater than buyPrice while both sides are enabled");
            }
        }
    }

    private void requireNonNegative(final List<String> errors, final String path, final BigDecimal value) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(path + " must be >= 0.00");
        }
    }

    private String buildMessage(final List<String> errors) {
        final StringBuilder builder = new StringBuilder("Pricing/config validation failed:");
        for (final String error : errors) {
            builder.append("\n - ").append(error);
        }
        return builder.toString();
    }
}

