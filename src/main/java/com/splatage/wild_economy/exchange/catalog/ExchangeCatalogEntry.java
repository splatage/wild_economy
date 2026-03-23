package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import java.math.BigDecimal;

public record ExchangeCatalogEntry(
        ItemKey itemKey,
        String displayName,
        ItemCategory category,
        GeneratedItemCategory generatedCategory,
        ItemPolicyMode policyMode,
        BigDecimal baseWorth,
        long stockCap,
        long turnoverAmountPerInterval,
        boolean buyEnabled,
        boolean sellEnabled,
        ResolvedEcoEntry eco
) {
    public record ResolvedEcoEntry(
            long minStockInclusive,
            long maxStockInclusive,
            BigDecimal buyPriceLowStock,
            BigDecimal buyPriceHighStock,
            BigDecimal sellPriceLowStock,
            BigDecimal sellPriceHighStock
    ) {
    }
}
