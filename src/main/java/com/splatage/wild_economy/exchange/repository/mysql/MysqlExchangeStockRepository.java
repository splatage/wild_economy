package com.splatage.wild_economy.exchange.repository.mysql;

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

public final class MysqlExchangeStockRepository implements ExchangeStockRepository {

    private final DatabaseProvider databaseProvider;

    public MysqlExchangeStockRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public long getStock(final ItemKey itemKey) {
        this.ensureRowExists(itemKey);
        final String sql = "SELECT stock_count FROM exchange_stock WHERE item_key = ?";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
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
    public void incrementStock(final ItemKey itemKey, final int amount) {
        this.changeStock(itemKey, amount);
    }

    @Override
    public void decrementStock(final ItemKey itemKey, final int amount) {
        this.changeStock(itemKey, -amount);
    }

    @Override
    public void setStock(final ItemKey itemKey, final long stock) {
        this.ensureRowExists(itemKey);
        final String sql = "UPDATE exchange_stock SET stock_count = ?, updated_at = ? WHERE item_key = ?";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Math.max(0L, stock));
            statement.setLong(2, Instant.now().getEpochSecond());
            statement.setString(3, itemKey.value());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to set stock for " + itemKey.value(), exception);
        }
    }

    private void changeStock(final ItemKey itemKey, final int delta) {
        this.ensureRowExists(itemKey);
        final long current = this.getStock(itemKey);
        final long updated = Math.max(0L, current + delta);
        this.setStock(itemKey, updated);
    }

    private void ensureRowExists(final ItemKey itemKey) {
        final String sql = "INSERT IGNORE INTO exchange_stock (item_key, stock_count, updated_at) VALUES (?, ?, ?)";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemKey.value());
            statement.setLong(2, 0L);
            statement.setLong(3, Instant.now().getEpochSecond());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to ensure stock row for " + itemKey.value(), exception);
        }
    }
}
