package com.splatage.wild_economy.store.repository;

import java.sql.Connection;
import java.util.UUID;

public interface StoreEntitlementRepository {
    boolean hasEntitlement(UUID playerId, String entitlementKey);
    void upsert(Connection connection, UUID playerId, String entitlementKey, String productId, long grantedAtEpochSecond);
}
