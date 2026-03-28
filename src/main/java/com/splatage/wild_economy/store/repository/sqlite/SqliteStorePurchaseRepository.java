package com.splatage.wild_economy.store.repository.sqlite;

import com.splatage.wild_economy.store.repository.StorePurchaseRepository;
import com.splatage.wild_economy.store.state.StorePurchaseAuditRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Objects;

public final class SqliteStorePurchaseRepository implements StorePurchaseRepository {

    private final String purchasesTableName;

    public SqliteStorePurchaseRepository(final String storeTablePrefix) {
        this.purchasesTableName = Objects.requireNonNull(storeTablePrefix, "storeTablePrefix") + "store_purchases";
    }

    @Override
    public void insertBatch(final Connection connection, final Collection<StorePurchaseAuditRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        final String sql = "INSERT INTO " + this.purchasesTableName
                + " (player_uuid, product_id, product_type, price_minor, status, failure_reason, created_at, completed_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (final StorePurchaseAuditRecord record : records) {
                statement.setString(1, record.playerId().toString());
                statement.setString(2, record.productId());
                statement.setString(3, record.productType().name());
                statement.setLong(4, record.price().minorUnits());
                statement.setString(5, record.status().name());
                statement.setString(6, record.failureReason());
                statement.setLong(7, record.createdAtEpochSecond());
                if (record.completedAtEpochSecond() == null) {
                    statement.setObject(8, null);
                } else {
                    statement.setLong(8, record.completedAtEpochSecond());
                }
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to batch insert store purchase records", exception);
        }
    }
}
