package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.repository.SupplierStatsRepository;
import com.splatage.wild_economy.exchange.supplier.SupplierAggregateRow;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SqliteSupplierStatsRepository implements SupplierStatsRepository {

    private final DatabaseProvider databaseProvider;
    private final String supplierAllTimeTableName;
    private final String supplierWeeklyTableName;

    public SqliteSupplierStatsRepository(final DatabaseProvider databaseProvider, final String exchangeTablePrefix) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
        final String prefix = Objects.requireNonNull(exchangeTablePrefix, "exchangeTablePrefix");
        this.supplierAllTimeTableName = prefix + "supplier_all_time";
        this.supplierWeeklyTableName = prefix + "supplier_weekly";
    }

    @Override
    public List<SupplierAggregateRow> loadAllTimeRows() {
        final String sql = "SELECT player_uuid, item_key, quantity_sold FROM " + this.supplierAllTimeTableName;
        return this.loadRows(sql, null);
    }

    @Override
    public List<SupplierAggregateRow> loadWeeklyRows(final String weekKey) {
        final String sql = "SELECT player_uuid, item_key, quantity_sold FROM " + this.supplierWeeklyTableName + " WHERE week_key = ?";
        return this.loadRows(sql, weekKey);
    }

    @Override
    public void incrementAllTime(final UUID playerId, final String itemKey, final long quantitySold, final long updatedAtEpochSecond) {
        final String sql = "INSERT INTO " + this.supplierAllTimeTableName + " (player_uuid, item_key, quantity_sold, updated_at) VALUES (?, ?, ?, ?) "
            + "ON CONFLICT(player_uuid, item_key) DO UPDATE SET quantity_sold = quantity_sold + excluded.quantity_sold, updated_at = excluded.updated_at";
        this.executeIncrement(sql, statement -> {
            statement.setString(1, playerId.toString());
            statement.setString(2, itemKey);
            statement.setLong(3, quantitySold);
            statement.setLong(4, updatedAtEpochSecond);
        });
    }

    @Override
    public void incrementWeekly(
        final String weekKey,
        final UUID playerId,
        final String itemKey,
        final long quantitySold,
        final long updatedAtEpochSecond
    ) {
        final String sql = "INSERT INTO " + this.supplierWeeklyTableName + " (week_key, player_uuid, item_key, quantity_sold, updated_at) VALUES (?, ?, ?, ?, ?) "
            + "ON CONFLICT(week_key, player_uuid, item_key) DO UPDATE SET quantity_sold = quantity_sold + excluded.quantity_sold, updated_at = excluded.updated_at";
        this.executeIncrement(sql, statement -> {
            statement.setString(1, weekKey);
            statement.setString(2, playerId.toString());
            statement.setString(3, itemKey);
            statement.setLong(4, quantitySold);
            statement.setLong(5, updatedAtEpochSecond);
        });
    }

    private List<SupplierAggregateRow> loadRows(final String sql, final String weekKey) {
        final List<SupplierAggregateRow> rows = new ArrayList<>();
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (weekKey != null) {
                statement.setString(1, weekKey);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SupplierAggregateRow(
                        UUID.fromString(resultSet.getString("player_uuid")),
                        resultSet.getString("item_key"),
                        resultSet.getLong("quantity_sold")
                    ));
                }
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load supplier aggregates", exception);
        }
        return rows;
    }

    private void executeIncrement(final String sql, final SqlBinder binder) {
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to update supplier aggregate", exception);
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
