package com.splatage.wild_economy.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationDomain;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MigrationBootstrapIntegrationTest {

    @TempDir
    Path tempDir;

    private DatabaseProvider databaseProvider;

    @AfterEach
    void tearDown() {
        if (this.databaseProvider != null) {
            this.databaseProvider.close();
        }
    }

    @Test
    void migrateAll_createsDistinctDomainSchemaTablesWhenExchangeAndStoreSharePrefix() throws Exception {
        final DatabaseConfig databaseConfig = this.databaseConfig("benchmark_", "benchmark_");
        this.databaseProvider = new DatabaseProvider(databaseConfig);
        final SchemaVersionRepository schemaVersionRepository = MigrationBootstrap.createSchemaVersionRepository(this.databaseProvider);

        MigrationBootstrap.migrateAll(this.databaseProvider, databaseConfig, schemaVersionRepository);

        assertTrue(this.tableExists("benchmark_exchange_schema_version"));
        assertTrue(this.tableExists("benchmark_store_schema_version"));
        assertTrue(this.tableExists("network_economy_schema_version"));

        assertTrue(this.tableExists("benchmark_exchange_stock"));
        assertTrue(this.tableExists("benchmark_store_purchases"));
        assertTrue(this.tableExists("network_economy_accounts"));

        assertEquals(3, schemaVersionRepository.getCurrentVersion(MigrationDomain.EXCHANGE.schemaVersionTableName(databaseConfig)));
        assertEquals(2, schemaVersionRepository.getCurrentVersion(MigrationDomain.ECONOMY.schemaVersionTableName(databaseConfig)));
        assertEquals(1, schemaVersionRepository.getCurrentVersion(MigrationDomain.STORE.schemaVersionTableName(databaseConfig)));
    }

    @Test
    void migrateAll_isIdempotentAcrossRepeatedRunsWithSharedPrefixes() throws Exception {
        final DatabaseConfig databaseConfig = this.databaseConfig("benchmark_", "benchmark_");
        this.databaseProvider = new DatabaseProvider(databaseConfig);
        final SchemaVersionRepository schemaVersionRepository = MigrationBootstrap.createSchemaVersionRepository(this.databaseProvider);

        MigrationBootstrap.migrateAll(this.databaseProvider, databaseConfig, schemaVersionRepository);
        final long firstExchangeAppliedAt = this.appliedAt("benchmark_exchange_schema_version");
        final long firstStoreAppliedAt = this.appliedAt("benchmark_store_schema_version");
        final long firstEconomyAppliedAt = this.appliedAt("network_economy_schema_version");

        MigrationBootstrap.migrateAll(this.databaseProvider, databaseConfig, schemaVersionRepository);

        assertEquals(1L, this.rowCount("benchmark_exchange_schema_version"));
        assertEquals(1L, this.rowCount("benchmark_store_schema_version"));
        assertEquals(1L, this.rowCount("network_economy_schema_version"));

        assertEquals(firstExchangeAppliedAt, this.appliedAt("benchmark_exchange_schema_version"));
        assertEquals(firstStoreAppliedAt, this.appliedAt("benchmark_store_schema_version"));
        assertEquals(firstEconomyAppliedAt, this.appliedAt("network_economy_schema_version"));
    }

    private DatabaseConfig databaseConfig(final String exchangePrefix, final String storePrefix) {
        final Path sqliteFile = this.tempDir.resolve("migration-bootstrap-test-" + Instant.now().toEpochMilli() + ".sqlite");
        return new DatabaseConfig(
            "sqlite",
            sqliteFile.toString(),
            null,
            0,
            null,
            null,
            null,
            false,
            2,
            "network_",
            exchangePrefix,
            storePrefix
        );
    }

    private boolean tableExists(final String tableName) throws Exception {
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?"
             )) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private long rowCount(final String tableName) throws Exception {
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long appliedAt(final String tableName) throws Exception {
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT applied_at FROM " + tableName + " LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
