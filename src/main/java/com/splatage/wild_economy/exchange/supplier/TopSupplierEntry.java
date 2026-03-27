package com.splatage.wild_economy.exchange.supplier;

import java.util.UUID;

public record TopSupplierEntry(
    int rank,
    UUID playerId,
    String displayName,
    long totalQuantitySold
) {
}
