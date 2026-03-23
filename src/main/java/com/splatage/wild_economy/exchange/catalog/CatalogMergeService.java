package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;

public final class CatalogMergeService {

    public ExchangeCatalogEntry toCatalogEntry(final ExchangeItemsConfig.RawItemEntry rawEntry) {
        return new ExchangeCatalogEntry(
                rawEntry.itemKey(),
                rawEntry.displayName(),
                rawEntry.category(),
                rawEntry.generatedCategory(),
                rawEntry.policyMode(),
                rawEntry.baseWorth(),
                rawEntry.stockCap(),
                rawEntry.turnoverAmountPerInterval(),
                rawEntry.buyEnabled(),
                rawEntry.sellEnabled(),
                new ExchangeCatalogEntry.ResolvedEcoEntry(
                        rawEntry.eco().minStockInclusive(),
                        rawEntry.eco().maxStockInclusive(),
                        rawEntry.eco().buyPriceLowStock(),
                        rawEntry.eco().buyPriceHighStock(),
                        rawEntry.eco().sellPriceLowStock(),
                        rawEntry.eco().sellPriceHighStock()
                )
        );
    }
}
