package com.splatage.wild_economy.exchange.repository.mysql;

import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.JdbcUtils;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MysqlExchangeTransactionRepository implements ExchangeTransactionRepository {

    private final DatabaseProvider databaseProvider;

    public MysqlExchangeTransactionRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public void insert(
        final TransactionType type,
        final UUID playerId,
        final String itemKey,
        final int amount,
        final BigDecimal unitPrice,
        final BigDecimal totalValue,
        final Instant createdAt,
        final String metaJson
    ) {
        final String sql = "INSERT INTO exchange_transactions "
            + "(transaction_type, player_uuid, item_key, amount, unit_price, total_value, created_at, meta_json) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, playerId.toString());
            statement.setString(3, itemKey);
            statement.setInt(4, amount);
            statement.setBigDecimal(5, unitPrice);
            statement.setBigDecimal(6, totalValue);
            statement.setLong(7, createdAt.getEpochSecond());
            JdbcUtils.bindNullableString(statement, 8, metaJson);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to insert exchange transaction", exception);
        }
    }
}
