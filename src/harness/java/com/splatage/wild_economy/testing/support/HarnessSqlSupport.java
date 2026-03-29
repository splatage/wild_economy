package com.splatage.wild_economy.testing.support;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.persistence.DatabaseDialect;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HarnessSqlSupport {

    private HarnessSqlSupport() {
    }

    public static void resetSeededTables(final HarnessBootstrap.HarnessComponents components) {
        final List<String> tables = List.of(
                components.databaseConfig().exchangeTablePrefix() + "exchange_transactions",
                components.databaseConfig().exchangeTablePrefix() + "supplier_weekly",
                components.databaseConfig().exchangeTablePrefix() + "supplier_all_time",
                components.databaseConfig().exchangeTablePrefix() + "exchange_stock",
                components.databaseConfig().storeTablePrefix() + "store_purchases",
                components.databaseConfig().storeTablePrefix() + "store_entitlements",
                components.databaseConfig().economyTablePrefix() + "economy_ledger",
                components.databaseConfig().economyTablePrefix() + "economy_name_cache",
                components.databaseConfig().economyTablePrefix() + "economy_accounts"
        );
        components.transactionRunner().run(connection -> {
            for (final String table : tables) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table)) {
                    statement.executeUpdate();
                }
            }
            return null;
        });
    }

    public static long countWhereLessThanZero(final Connection connection, final String table, final String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " < 0"
        )) {
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    public static Set<String> distinctStrings(final Connection connection, final String table, final String column) throws SQLException {
        final Set<String> values = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT " + column + " FROM " + table
        )) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                }
            }
        }
        return values;
    }

    public static List<Long> longColumn(final Connection connection, final String sql) throws SQLException {
        final List<Long> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.add(resultSet.getLong(1));
                }
            }
        }
        return values;
    }

    public static BigDecimal sumPriceMinor(final Connection connection, final String table) throws SQLException {
        final String sql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite")
                ? "SELECT COALESCE(SUM(price_minor), 0) FROM " + table
                : "SELECT COALESCE(SUM(price_minor), 0) FROM " + table;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    public static DatabaseDialect dialect(final HarnessBootstrap.HarnessComponents components) {
        return components.databaseProvider().dialect();
    }
}
