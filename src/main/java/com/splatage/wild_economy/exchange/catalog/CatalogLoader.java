package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.catalog.rootvalue.RootValueLoader;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogLoader {

    public ExchangeCatalog load(final ExchangeItemsConfig exchangeItemsConfig) {
        return this.load(exchangeItemsConfig, RootValueLoader.empty());
    }

    public ExchangeCatalog load(
        final ExchangeItemsConfig exchangeItemsConfig,
        final RootValueLookup rootValueLookup
    ) {
        final Map<com.splatage.wild_economy.exchange.domain.ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>();

        for (final ExchangeItemsConfig.RawItemEntry rawEntry : exchangeItemsConfig.items().values()) {
            entries.put(rawEntry.itemKey(), this.toCatalogEntry(rawEntry));
        }

        return new ExchangeCatalog(entries, rootValueLookup);
    }

    private ExchangeCatalogEntry toCatalogEntry(final ExchangeItemsConfig.RawItemEntry rawEntry) {
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
