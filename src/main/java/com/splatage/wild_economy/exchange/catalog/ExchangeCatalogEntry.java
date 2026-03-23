package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import java.math.BigDecimal;
import java.util.List;

public record ExchangeCatalogEntry(
    ItemKey itemKey,
    String displayName,
    ItemCategory category,
    GeneratedItemCategory generatedCategory,
    ItemPolicyMode policyMode,
    BigDecimal baseWorth,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    long stockCap,
    long turnoverAmountPerInterval,
    long initialStock,
    List<SellPriceBand> sellPriceBands,
    boolean buyEnabled,
    boolean sellEnabled
) {
}
