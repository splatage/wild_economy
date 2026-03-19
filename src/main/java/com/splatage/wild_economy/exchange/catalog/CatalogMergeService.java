package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.Map;

public final class CatalogMergeService {

    public ExchangeCatalogEntry merge(
        final ExchangeItemsConfig.RawItemEntry rawEntry,
        final Map<ItemKey, BigDecimal> importedWorths,
        final WorthImportConfig worthImportConfig
    ) {
        final BigDecimal importedWorth = importedWorths.get(rawEntry.itemKey());
        final BigDecimal baseWorth = importedWorth != null ? importedWorth : BigDecimal.ZERO;

        final BigDecimal buyPrice = this.resolvePrice(
            rawEntry.buyPrice(),
            worthImportConfig.useWorthAsBaseValue() ? importedWorth : null
        );

        final BigDecimal sellPrice = this.resolvePrice(
            rawEntry.sellPrice(),
            worthImportConfig.useWorthAsBaseValue() ? importedWorth : null
        );

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

    private BigDecimal resolvePrice(final BigDecimal explicitPrice, final BigDecimal importedFallback) {
        if (explicitPrice != null) {
            return explicitPrice;
        }
        if (importedFallback != null) {
            return importedFallback;
        }
        return BigDecimal.ZERO;
    }
}
