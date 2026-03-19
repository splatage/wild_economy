package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.Map;

public final class CatalogMergeService {

    public ExchangeCatalogEntry merge(
        final ExchangeItemsConfig.RawItemEntry rawEntry,
        final Map<ItemKey, BigDecimal> importedRootValues
    ) {
        final BigDecimal rootValue = importedRootValues.get(rawEntry.itemKey());
        final BigDecimal baseWorth = rootValue != null ? rootValue : BigDecimal.ZERO;

        final BigDecimal buyPrice = this.resolvePrice(rawEntry.buyPrice(), rootValue);
        final BigDecimal sellPrice = this.resolvePrice(rawEntry.sellPrice(), rootValue);

        return new ExchangeCatalogEntry(
            rawEntry.itemKey(),
            rawEntry.displayName(),
            rawEntry.category(),
            rawEntry.policyMode(),
            baseWorth,
            buyPrice,
            sellPrice,
            rawEntry.stockCap(),
            rawEntry.turnoverAmountPerInterval(),
            rawEntry.sellPriceBands(),
            rawEntry.buyEnabled(),
            rawEntry.sellEnabled()
        );
    }

    private BigDecimal resolvePrice(final BigDecimal explicitPrice, final BigDecimal rootValueFallback) {
        if (explicitPrice != null) {
            return explicitPrice;
        }
        if (rootValueFallback != null) {
            return rootValueFallback;
        }
        return BigDecimal.ZERO;
    }
}
