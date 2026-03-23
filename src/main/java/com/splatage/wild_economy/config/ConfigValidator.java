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
    private final ExchangeCatalog exchangeCatalog;

    public ConfigValidator(
            final ExchangeItemsConfig exchangeItemsConfig,
            final ExchangeCatalog exchangeCatalog
    ) {
        this.exchangeItemsConfig = Objects.requireNonNull(exchangeItemsConfig, "exchangeItemsConfig");
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
    }

    public void validate() {
        final List<String> errors = new ArrayList<>();
        this.validatePublishedRuntimeExchangeItems(errors);
        this.validateMergedCatalog(errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException(this.buildMessage(errors));
        }
    }

    private void validatePublishedRuntimeExchangeItems(final List<String> errors) {
        for (final Map.Entry<ItemKey, ExchangeItemsConfig.RawItemEntry> entry : this.exchangeItemsConfig.items().entrySet()) {
            final ItemKey itemKey = entry.getKey();
            final ExchangeItemsConfig.RawItemEntry rawItem = entry.getValue();
            final String prefix = "exchange-items.items." + itemKey.value();

            if (rawItem.stockCap() < 0L) {
                errors.add(prefix + ".stock-cap must be >= 0");
            }
            if (rawItem.turnoverAmountPerInterval() < 0L) {
                errors.add(prefix + ".turnover-amount-per-interval must be >= 0");
            }

            this.requireNonNegative(errors, prefix + ".base-worth", rawItem.baseWorth());

            final ExchangeItemsConfig.ResolvedEcoEntry eco = rawItem.eco();
            if (eco == null) {
                errors.add(prefix + ".eco is required");
                continue;
            }

            if (eco.minStockInclusive() < 0L) {
                errors.add(prefix + ".eco.min-stock must be >= 0");
            }
            if (eco.maxStockInclusive() < eco.minStockInclusive()) {
                errors.add(prefix + ".eco.max-stock must be >= eco.min-stock");
            }

            this.requireNonNegative(errors, prefix + ".eco.buy-price-at-min-stock", eco.buyPriceLowStock());
            this.requireNonNegative(errors, prefix + ".eco.buy-price-at-max-stock", eco.buyPriceHighStock());
            this.requireNonNegative(errors, prefix + ".eco.sell-price-at-min-stock", eco.sellPriceLowStock());
            this.requireNonNegative(errors, prefix + ".eco.sell-price-at-max-stock", eco.sellPriceHighStock());

            if (rawItem.buyEnabled()) {
                this.requirePresent(errors, prefix + ".eco.buy-price-at-min-stock", eco.buyPriceLowStock());
                this.requirePresent(errors, prefix + ".eco.buy-price-at-max-stock", eco.buyPriceHighStock());
            }
            if (rawItem.sellEnabled()) {
                this.requirePresent(errors, prefix + ".eco.sell-price-at-min-stock", eco.sellPriceLowStock());
                this.requirePresent(errors, prefix + ".eco.sell-price-at-max-stock", eco.sellPriceHighStock());
            }

            if (rawItem.buyEnabled()
                    && rawItem.sellEnabled()
                    && eco.buyPriceLowStock() != null
                    && eco.buyPriceHighStock() != null
                    && eco.sellPriceLowStock() != null
                    && eco.sellPriceHighStock() != null) {
                if (eco.sellPriceLowStock().compareTo(eco.buyPriceLowStock()) > 0) {
                    errors.add(prefix + " has arbitrage risk at low stock: sell price exceeds buy price");
                }
                if (eco.sellPriceHighStock().compareTo(eco.buyPriceHighStock()) > 0) {
                    errors.add(prefix + " has arbitrage risk at high stock: sell price exceeds buy price");
                }
            }
        }
    }

    private void validateMergedCatalog(final List<String> errors) {
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.allEntries()) {
            final String prefix = "catalog." + entry.itemKey().value();

            if (entry.stockCap() < 0L) {
                errors.add(prefix + ".stockCap must be >= 0");
            }
            if (entry.turnoverAmountPerInterval() < 0L) {
                errors.add(prefix + ".turnoverAmountPerInterval must be >= 0");
            }

            this.requireNonNegative(errors, prefix + ".baseWorth", entry.baseWorth());

            if (entry.eco() == null) {
                errors.add(prefix + ".eco must be present in the merged runtime catalog");
                continue;
            }

            if (entry.eco().minStockInclusive() < 0L) {
                errors.add(prefix + ".eco.minStockInclusive must be >= 0");
            }
            if (entry.eco().maxStockInclusive() < entry.eco().minStockInclusive()) {
                errors.add(prefix + ".eco.maxStockInclusive must be >= eco.minStockInclusive");
            }

            this.requireNonNegative(errors, prefix + ".eco.buyPriceLowStock", entry.eco().buyPriceLowStock());
            this.requireNonNegative(errors, prefix + ".eco.buyPriceHighStock", entry.eco().buyPriceHighStock());
            this.requireNonNegative(errors, prefix + ".eco.sellPriceLowStock", entry.eco().sellPriceLowStock());
            this.requireNonNegative(errors, prefix + ".eco.sellPriceHighStock", entry.eco().sellPriceHighStock());

            if (entry.buyEnabled()
                    && entry.sellEnabled()
                    && entry.eco().sellPriceLowStock().compareTo(entry.eco().buyPriceLowStock()) > 0) {
                errors.add(prefix + " has arbitrage risk at low stock: sell price exceeds buy price");
            }
            if (entry.buyEnabled()
                    && entry.sellEnabled()
                    && entry.eco().sellPriceHighStock().compareTo(entry.eco().buyPriceHighStock()) > 0) {
                errors.add(prefix + " has arbitrage risk at high stock: sell price exceeds buy price");
            }
        }
    }

    private void requirePresent(final List<String> errors, final String path, final Object value) {
        if (value == null) {
            errors.add(path + " is required");
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
