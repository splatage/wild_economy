package com.splatage.wild_economy.store.repository;

import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StorePurchaseStatus;
import java.sql.Connection;
import java.util.UUID;

public interface StorePurchaseRepository {
    void insert(
        Connection connection,
        UUID playerId,
        String productId,
        StoreProductType productType,
        MoneyAmount price,
        StorePurchaseStatus status,
        String failureReason,
        long createdAtEpochSecond,
        Long completedAtEpochSecond
    );
}
