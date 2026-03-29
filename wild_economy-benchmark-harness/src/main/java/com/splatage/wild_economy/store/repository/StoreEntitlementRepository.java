package com.splatage.wild_economy.store.repository;

import com.splatage.wild_economy.store.state.DirtyEntitlementGrant;
import com.splatage.wild_economy.store.state.StoreEntitlementRecord;
import java.sql.Connection;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface StoreEntitlementRepository {
    Map<String, StoreEntitlementRecord> loadPlayerEntitlements(UUID playerId);
    void upsertBatch(Connection connection, UUID playerId, Collection<DirtyEntitlementGrant> grants);
}
