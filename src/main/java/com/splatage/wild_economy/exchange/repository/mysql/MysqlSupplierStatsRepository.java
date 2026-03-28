package com.splatage.wild_economy.exchange.repository.mysql;

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

public final class MysqlSupplierStatsRepository implements SupplierStatsRepository {

    private final DatabaseProvider databaseProvider;
    private final String supplierAllTimeTableName;
    private final String supplierWeeklyTableName;

    public MysqlSupplierStatsRepository(final DatabaseProvider databaseProvider, final String exchangeTablePrefix) {
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
    public void recordSaleContribution(
        final String weekKey,
        final UUID playerId,
        final String itemKey,
        final long quantitySold,
        final long updatedAtEpochSecond
    ) {
        final String allTimeSql = "INSERT INTO " + this.supplierAllTimeTableName + " (player_uuid, item_key, quantity_sold, updated_at) VALUES (?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE quantity_sold = quantity_sold + VALUES(quantity_sold), updated_at = VALUES(updated_at)";
        final String weeklySql = "INSERT INTO " + this.supplierWeeklyTableName + " (week_key, player_uuid, item_key, quantity_sold, updated_at) VALUES (?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE quantity_sold = quantity_sold + VALUES(quantity_sold), updated_at = VALUES(updated_at)";

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (
                PreparedStatement allTimeStatement = connection.prepareStatement(allTimeSql);
                PreparedStatement weeklyStatement = connection.prepareStatement(weeklySql)
            ) {
                allTimeStatement.setString(1, playerId.toString());
                allTimeStatement.setString(2, itemKey);
                allTimeStatement.setLong(3, quantitySold);
                allTimeStatement.setLong(4, updatedAtEpochSecond);
                allTimeStatement.executeUpdate();

                weeklyStatement.setString(1, weekKey);
                weeklyStatement.setString(2, playerId.toString());
                weeklyStatement.setString(3, itemKey);
                weeklyStatement.setLong(4, quantitySold);
                weeklyStatement.setLong(5, updatedAtEpochSecond);
                weeklyStatement.executeUpdate();

                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to record supplier contribution", exception);
        }
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

}
