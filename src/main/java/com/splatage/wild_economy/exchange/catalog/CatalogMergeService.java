package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class CatalogMergeService {

    public ExchangeCatalogEntry merge(
        final ExchangeCatalogEntry baseEntry,
        final ExchangeItemsConfig.RawItemEntry overrideEntry,
        final Map<ItemKey, BigDecimal> importedRootValues
    ) {
        final BigDecimal rootValue = importedRootValues.get(overrideEntry.itemKey());
        final BigDecimal baseWorth = this.resolveBaseWorth(baseEntry, rootValue);
        final BigDecimal buyPrice = this.resolvePrice(overrideEntry.buyPrice(), baseEntry != null ? baseEntry.buyPrice() : null, rootValue);
        final BigDecimal sellPrice = this.resolvePrice(overrideEntry.sellPrice(), baseEntry != null ? baseEntry.sellPrice() : null, rootValue);

        final String displayName = overrideEntry.displayName() != null
            ? overrideEntry.displayName()
            : baseEntry != null ? baseEntry.displayName() : overrideEntry.itemKey().value();

        final ItemCategory category = overrideEntry.category() != null
            ? overrideEntry.category()
            : baseEntry != null ? baseEntry.category() : ItemCategory.MISC;

        final GeneratedItemCategory generatedCategory = overrideEntry.generatedCategory() != null
            ? overrideEntry.generatedCategory()
            : baseEntry != null ? baseEntry.generatedCategory() : GeneratedItemCategory.MISC;

        final List sellPriceBands = overrideEntry.sellPriceBands() != null
            ? overrideEntry.sellPriceBands()
            : baseEntry != null ? baseEntry.sellPriceBands() : List.of();

        return new ExchangeCatalogEntry(
            overrideEntry.itemKey(),
            displayName,
            category,
            generatedCategory,
            overrideEntry.policyMode(),
            baseWorth,
            buyPrice,
            sellPrice,
            overrideEntry.stockCap(),
            overrideEntry.turnoverAmountPerInterval(),
            sellPriceBands,
            overrideEntry.buyEnabled(),
            overrideEntry.sellEnabled()
        );
    }

    private BigDecimal resolveBaseWorth(
        final ExchangeCatalogEntry baseEntry,
        final BigDecimal rootValue
    ) {
        if (baseEntry != null && baseEntry.baseWorth() != null) {
            return baseEntry.baseWorth();
        }
        if (rootValue != null) {
            return rootValue;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolvePrice(
        final BigDecimal explicitPrice,
        final BigDecimal baseCatalogPrice,
        final BigDecimal rootValueFallback
    ) {
        if (explicitPrice != null) {
            return explicitPrice;
        }
        if (baseCatalogPrice != null) {
            return baseCatalogPrice;
        }
        if (rootValueFallback != null) {
            return rootValueFallback;
        }
        return BigDecimal.ZERO;
    }
}
