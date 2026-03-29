package com.splatage.wild_economy.store.repository.sqlite;

import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.store.repository.StoreEntitlementRepository;
import com.splatage.wild_economy.store.state.DirtyEntitlementGrant;
import com.splatage.wild_economy.store.state.StoreEntitlementRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SqliteStoreEntitlementRepository implements StoreEntitlementRepository {

    private final DatabaseProvider databaseProvider;
    private final String entitlementsTableName;

    public SqliteStoreEntitlementRepository(final DatabaseProvider databaseProvider, final String storeTablePrefix) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
        this.entitlementsTableName = Objects.requireNonNull(storeTablePrefix, "storeTablePrefix") + "store_entitlements";
    }

    @Override
    public Map<String, StoreEntitlementRecord> loadPlayerEntitlements(final UUID playerId) {
        final String sql = "SELECT entitlement_key, product_id, granted_at FROM " + this.entitlementsTableName + " WHERE player_uuid = ?";
        final Map<String, StoreEntitlementRecord> entitlements = new LinkedHashMap<>();
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final StoreEntitlementRecord record = new StoreEntitlementRecord(
                            resultSet.getString("entitlement_key"),
                            resultSet.getString("product_id"),
                            resultSet.getLong("granted_at")
                    );
                    entitlements.put(record.entitlementKey(), record);
                }
            }
            connection.commit();
            return entitlements;
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load store entitlements for " + playerId, exception);
        }
    }

    @Override
    public void upsertBatch(
        final Connection connection,
        final UUID playerId,
        final Collection<DirtyEntitlementGrant> grants
    ) {
        if (grants.isEmpty()) {
            return;
        }

        final String sql = """
            INSERT INTO %s (player_uuid, entitlement_key, product_id, granted_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid, entitlement_key) DO UPDATE SET
                product_id = excluded.product_id,
                granted_at = excluded.granted_at
            """.formatted(this.entitlementsTableName);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (final DirtyEntitlementGrant grant : grants) {
                statement.setString(1, playerId.toString());
                statement.setString(2, grant.entitlementKey());
                statement.setString(3, grant.productId());
                statement.setLong(4, grant.grantedAtEpochSecond());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to batch upsert store entitlements for " + playerId, exception);
        }
    }
}
