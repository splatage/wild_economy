package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.activity.MarketActivityRecord;
import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.JdbcUtils;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SqliteExchangeTransactionRepository implements ExchangeTransactionRepository {

    private final DatabaseProvider databaseProvider;
    private final String exchangeTransactionsTableName;

    public SqliteExchangeTransactionRepository(final DatabaseProvider databaseProvider, final String exchangeTablePrefix) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
        this.exchangeTransactionsTableName = Objects.requireNonNull(exchangeTablePrefix, "exchangeTablePrefix") + "exchange_transactions";
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
        final String sql = "INSERT INTO " + this.exchangeTransactionsTableName + " "
            + "(transaction_type, player_uuid, item_key, amount, unit_price, total_value, created_at, meta_json) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, type.name());
                statement.setString(2, playerId.toString());
                statement.setString(3, itemKey);
                statement.setInt(4, amount);
                statement.setBigDecimal(5, unitPrice);
                statement.setBigDecimal(6, totalValue);
                statement.setLong(7, createdAt.getEpochSecond());
                JdbcUtils.bindNullableString(statement, 8, metaJson);
                statement.executeUpdate();
                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to insert exchange transaction", exception);
        }
    }

    @Override
    public List<MarketActivityRecord> loadRecentlyPurchased(final long sinceEpochSecond, final int limit) {
        final String sql = "SELECT item_key, MAX(created_at) AS event_epoch, MAX(total_value) AS total_value, 0 AS amount "
            + "FROM " + this.exchangeTransactionsTableName + " "
            + "WHERE transaction_type = 'BUY' AND created_at >= ? "
            + "GROUP BY item_key ORDER BY event_epoch DESC, total_value DESC LIMIT ?";
        return this.loadMarketActivity(sql, statement -> {
            statement.setLong(1, sinceEpochSecond);
            statement.setInt(2, limit);
        });
    }

    @Override
    public List<MarketActivityRecord> loadTopTurnover(final long sinceEpochSecond, final int limit) {
        final String sql = "SELECT item_key, MAX(created_at) AS event_epoch, COALESCE(SUM(total_value), 0) AS total_value, COALESCE(SUM(amount), 0) AS amount "
            + "FROM " + this.exchangeTransactionsTableName + " "
            + "WHERE transaction_type = 'BUY' AND created_at >= ? "
            + "GROUP BY item_key ORDER BY total_value DESC, event_epoch DESC LIMIT ?";
        return this.loadMarketActivity(sql, statement -> {
            statement.setLong(1, sinceEpochSecond);
            statement.setInt(2, limit);
        });
    }

    @Override
    public List<MarketActivityRecord> loadPlayerRecentPurchases(
        final UUID playerId,
        final long sinceEpochSecond,
        final int limit
    ) {
        final String sql = "SELECT item_key, MAX(created_at) AS event_epoch, COALESCE(SUM(total_value), 0) AS total_value, COALESCE(SUM(amount), 0) AS amount "
            + "FROM " + this.exchangeTransactionsTableName + " "
            + "WHERE transaction_type = 'BUY' AND player_uuid = ? AND created_at >= ? "
            + "GROUP BY item_key ORDER BY event_epoch DESC, total_value DESC LIMIT ?";
        return this.loadMarketActivity(sql, statement -> {
            statement.setString(1, playerId.toString());
            statement.setLong(2, sinceEpochSecond);
            statement.setInt(3, limit);
        });
    }

    private List<MarketActivityRecord> loadMarketActivity(
        final String sql,
        final StatementBinder binder
    ) {
        final List<MarketActivityRecord> rows = new ArrayList<>();
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new MarketActivityRecord(
                        resultSet.getString("item_key"),
                        resultSet.getLong("event_epoch"),
                        resultSet.getBigDecimal("total_value"),
                        resultSet.getInt("amount")
                    ));
                }
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load market activity", exception);
        }
        return rows;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
