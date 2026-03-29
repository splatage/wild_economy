package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

public final class SqliteSchemaVersionRepository implements SchemaVersionRepository {

    private final DatabaseProvider databaseProvider;

    public SqliteSchemaVersionRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public int getCurrentVersion(final String tableName) {
        final String sql = "SELECT version FROM " + tableName + " ORDER BY version DESC LIMIT 1";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("version");
            }
            return 0;
        } catch (final SQLException exception) {
            return 0;
        }
    }

    @Override
    public void setCurrentVersion(final String tableName, final int version) {
        final String deleteSql = "DELETE FROM " + tableName;
        final String insertSql = "INSERT INTO " + tableName + " (version, applied_at) VALUES (?, ?)";

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                deleteStatement.executeUpdate();
            }

            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                insertStatement.setInt(1, version);
                insertStatement.setLong(2, Instant.now().getEpochSecond());
                insertStatement.executeUpdate();
            }

            connection.commit();
        } catch (final SQLException exception) {
            throw new IllegalStateException(
                    "Failed to set schema version to " + version + " in table '" + tableName + "'",
                    exception
            );
        }
    }
}
