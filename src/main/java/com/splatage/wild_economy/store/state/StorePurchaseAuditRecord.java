package com.splatage.wild_economy.store.state;

import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StorePurchaseStatus;
import java.util.UUID;

public record StorePurchaseAuditRecord(
    UUID playerId,
    String productId,
    StoreProductType productType,
    MoneyAmount price,
    StorePurchaseStatus status,
    String failureReason,
    long createdAtEpochSecond,
    Long completedAtEpochSecond
) {
}
