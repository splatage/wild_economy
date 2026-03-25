package com.splatage.wild_economy.store.repository.mysql;

import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.store.repository.StoreEntitlementRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class MysqlStoreEntitlementRepository implements StoreEntitlementRepository {

    private final DatabaseProvider databaseProvider;
    private final String entitlementsTableName;

    public MysqlStoreEntitlementRepository(final DatabaseProvider databaseProvider, final String economyTablePrefix) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
        this.entitlementsTableName = Objects.requireNonNull(economyTablePrefix, "economyTablePrefix") + "store_entitlements";
    }

    @Override
    public boolean hasEntitlement(final UUID playerId, final String entitlementKey) {
        final String sql = "SELECT 1 FROM " + this.entitlementsTableName + " WHERE player_uuid = ? AND entitlement_key = ?";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, entitlementKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to check store entitlement for " + playerId, exception);
        }
    }

    @Override
    public void upsert(
        final Connection connection,
        final UUID playerId,
        final String entitlementKey,
        final String productId,
        final long grantedAtEpochSecond
    ) {
        final String sql = """
            INSERT INTO %s (player_uuid, entitlement_key, product_id, granted_at)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                product_id = VALUES(product_id),
                granted_at = VALUES(granted_at)
            """.formatted(this.entitlementsTableName);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, entitlementKey);
            statement.setString(3, productId);
            statement.setLong(4, grantedAtEpochSecond);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to upsert store entitlement for " + playerId, exception);
        }
    }
}
