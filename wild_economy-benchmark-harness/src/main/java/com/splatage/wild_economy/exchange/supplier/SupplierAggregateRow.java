package com.splatage.wild_economy.exchange.supplier;

import java.util.UUID;

public record SupplierAggregateRow(
    UUID playerId,
    String itemKey,
    long quantitySold
) {
}
