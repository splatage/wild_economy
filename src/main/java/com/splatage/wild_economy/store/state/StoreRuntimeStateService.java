package com.splatage.wild_economy.store.state;

import java.util.UUID;

public interface StoreRuntimeStateService {
    void ensurePlayerLoadedAsync(UUID playerId);
    StoreOwnershipState getOwnershipState(UUID playerId, String entitlementKey);
    void grantEntitlement(UUID playerId, String entitlementKey, String productId, long grantedAtEpochSecond);
    void recordPurchase(StorePurchaseAuditRecord record);
    void handlePlayerQuit(UUID playerId);
    void flushDirtyNow();
    void shutdown();
}
