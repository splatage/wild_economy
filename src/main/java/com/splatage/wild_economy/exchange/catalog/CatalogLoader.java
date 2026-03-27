package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.gui.layout.LayoutPlacement;
import com.splatage.wild_economy.gui.layout.LayoutPlacementResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class CatalogLoader {

    private final LayoutPlacementResolver layoutPlacementResolver;

    public CatalogLoader(final LayoutPlacementResolver layoutPlacementResolver) {
        this.layoutPlacementResolver = Objects.requireNonNull(layoutPlacementResolver, "layoutPlacementResolver");
    }

    public ExchangeCatalog load(final ExchangeItemsConfig exchangeItemsConfig) {
        final Map<com.splatage.wild_economy.exchange.domain.ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>();

        for (final ExchangeItemsConfig.RawItemEntry rawEntry : exchangeItemsConfig.items().values()) {
            entries.put(rawEntry.itemKey(), this.toCatalogEntry(rawEntry));
        }

        return new ExchangeCatalog(entries);
    }

    private ExchangeCatalogEntry toCatalogEntry(final ExchangeItemsConfig.RawItemEntry rawEntry) {
        final LayoutPlacement layoutPlacement = this.layoutPlacementResolver.resolve(rawEntry.itemKey());
        return new ExchangeCatalogEntry(
                rawEntry.itemKey(),
                rawEntry.displayName(),
                rawEntry.category(),
                rawEntry.generatedCategory(),
                layoutPlacement.groupKey(),
                layoutPlacement.childKey(),
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
