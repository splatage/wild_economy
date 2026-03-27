package com.splatage.wild_economy.exchange.supplier;

import com.splatage.wild_economy.exchange.domain.ItemKey;

public record SupplierContributionEntry(
    ItemKey itemKey,
    String displayName,
    long quantitySold
) {
}
