package com.splatage.wild_economy.store.repository.mysql;

import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StorePurchaseStatus;
import com.splatage.wild_economy.store.repository.StorePurchaseRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class MysqlStorePurchaseRepository implements StorePurchaseRepository {

    private final String purchasesTableName;

    public MysqlStorePurchaseRepository(final String economyTablePrefix) {
        this.purchasesTableName = Objects.requireNonNull(economyTablePrefix, "economyTablePrefix") + "store_purchases";
    }

    @Override
    public void insert(
        final Connection connection,
        final UUID playerId,
        final String productId,
        final StoreProductType productType,
        final MoneyAmount price,
        final StorePurchaseStatus status,
        final String failureReason,
        final long createdAtEpochSecond,
        final Long completedAtEpochSecond
    ) {
        final String sql = "INSERT INTO " + this.purchasesTableName
                + " (player_uuid, product_id, product_type, price_minor, status, failure_reason, created_at, completed_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, productId);
            statement.setString(3, productType.name());
            statement.setLong(4, price.minorUnits());
            statement.setString(5, status.name());
            statement.setString(6, failureReason);
            statement.setLong(7, createdAtEpochSecond);
            if (completedAtEpochSecond == null) {
                statement.setObject(8, null);
            } else {
                statement.setLong(8, completedAtEpochSecond);
            }
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to insert store purchase record", exception);
        }
    }
}
