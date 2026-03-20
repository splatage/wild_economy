package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SqliteExchangeStockRepository implements ExchangeStockRepository {

    private final DatabaseProvider databaseProvider;

    public SqliteExchangeStockRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public long getStock(final ItemKey itemKey) {
        final String sql = "SELECT stock_count FROM exchange_stock WHERE item_key = ?";
        try (
            Connection connection = this.databaseProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, itemKey.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("stock_count");
                }
                return 0L;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load stock for " + itemKey.value(), exception);
        }
    }

    @Override
    public Map<ItemKey, Long> getStocks(final Iterable<ItemKey> itemKeys) {
        final Map<ItemKey, Long> result = new LinkedHashMap<>();
        for (final ItemKey itemKey : itemKeys) {
            result.put(itemKey, this.getStock(itemKey));
        }
        return result;
    }

    @Override
    public Map<ItemKey, Long> loadAllStocks() {
        final String sql = "SELECT item_key, stock_count FROM exchange_stock";
        final Map<ItemKey, Long> result = new LinkedHashMap<>();
        try (
            Connection connection = this.databaseProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                result.put(new ItemKey(resultSet.getString("item_key")), resultSet.getLong("stock_count"));
            }
            return result;
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load exchange stock cache", exception);
        }
    }

    @Override
    public void incrementStock(final ItemKey itemKey, final int amount) {
        if (amount <= 0) {
            return;
        }
        this.changeStockAtomic(itemKey, amount);
    }

    @Override
    public void decrementStock(final ItemKey itemKey, final int amount) {
        if (amount <= 0) {
            return;
        }
        this.changeStockAtomic(itemKey, -amount);
    }

    @Override
    public void setStock(final ItemKey itemKey, final long stock) {
        this.flushStocks(Map.of(itemKey, stock));
    }

    @Override
    public void flushStocks(final Map<ItemKey, Long> stockByItemKey) {
        if (stockByItemKey.isEmpty()) {
            return;
        }

        final String sql = """
            INSERT INTO exchange_stock (item_key, stock_count, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(item_key) DO UPDATE SET
                stock_count = excluded.stock_count,
                updated_at = excluded.updated_at
            """;

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                final long updatedAt = Instant.now().getEpochSecond();
                for (final Map.Entry<ItemKey, Long> entry : stockByItemKey.entrySet()) {
                    statement.setString(1, entry.getKey().value());
                    statement.setLong(2, Math.max(0L, entry.getValue()));
                    statement.setLong(3, updatedAt);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to batch flush exchange stock", exception);
        }
    }

    private void changeStockAtomic(final ItemKey itemKey, final int delta) {
        final String insertSql = "INSERT OR IGNORE INTO exchange_stock (item_key, stock_count, updated_at) VALUES (?, ?, ?)";
        final String updateSql = "UPDATE exchange_stock SET stock_count = MAX(0, stock_count + ?), updated_at = ? WHERE item_key = ?";

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (
                PreparedStatement insertStatement = connection.prepareStatement(insertSql);
                PreparedStatement updateStatement = connection.prepareStatement(updateSql)
            ) {
                final long updatedAt = Instant.now().getEpochSecond();

                insertStatement.setString(1, itemKey.value());
                insertStatement.setLong(2, 0L);
                insertStatement.setLong(3, updatedAt);
                insertStatement.executeUpdate();

                updateStatement.setInt(1, delta);
                updateStatement.setLong(2, updatedAt);
                updateStatement.setString(3, itemKey.value());
                updateStatement.executeUpdate();

                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to atomically change stock for " + itemKey.value(), exception);
        }
    }
}
