package com.splatage.wild_economy.exchange.supplier;

import java.util.List;
import java.util.UUID;

public record SupplierPlayerDetail(
    SupplierScope scope,
    UUID playerId,
    String displayName,
    int rank,
    long totalQuantitySold,
    List<SupplierContributionEntry> topContributions
) {
}
