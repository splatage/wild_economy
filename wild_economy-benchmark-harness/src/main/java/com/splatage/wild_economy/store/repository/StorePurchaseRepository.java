package com.splatage.wild_economy.store.repository;

import com.splatage.wild_economy.store.state.StorePurchaseAuditRecord;
import java.sql.Connection;
import java.util.Collection;

public interface StorePurchaseRepository {
    void insertBatch(Connection connection, Collection<StorePurchaseAuditRecord> records);
}
