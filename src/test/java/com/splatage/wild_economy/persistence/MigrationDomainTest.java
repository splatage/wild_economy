package com.splatage.wild_economy.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.config.DatabaseConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MigrationDomainTest {

    private static final DatabaseConfig DATABASE_CONFIG = new DatabaseConfig(
            "sqlite",
            "plugins/wild_economy/data.db",
            "localhost",
            3306,
            "minecraft",
            "user",
            "password",
            false,
            10,
            "network_",
            "benchmark_",
            "store_"
    );

    @Test
    void schemaVersionTablesAreDomainSpecific() {
        assertEquals("benchmark_exchange_schema_version", MigrationDomain.EXCHANGE.schemaVersionTableName(DATABASE_CONFIG));
        assertEquals("network_economy_schema_version", MigrationDomain.ECONOMY.schemaVersionTableName(DATABASE_CONFIG));
        assertEquals("store_store_schema_version", MigrationDomain.STORE.schemaVersionTableName(DATABASE_CONFIG));
    }

    @Test
    void exchangeAndStoreRemainDistinctEvenWhenPrefixesMatch() {
        final DatabaseConfig sharedPrefixConfig = new DatabaseConfig(
                "sqlite",
                "plugins/wild_economy/data.db",
                "localhost",
                3306,
                "minecraft",
                "user",
                "password",
                false,
                10,
                "network_",
                "benchmark_",
                "benchmark_"
        );

        assertEquals("benchmark_exchange_schema_version", MigrationDomain.EXCHANGE.schemaVersionTableName(sharedPrefixConfig));
        assertEquals("benchmark_store_schema_version", MigrationDomain.STORE.schemaVersionTableName(sharedPrefixConfig));
    }

    @Test
    void storeDomainUsesDedicatedResourceDirectory() {
        assertEquals("store", MigrationDomain.STORE.resourceDirectoryName());
    }

    @Test
    void storeFoundationMigrationsExistUnderDedicatedStoreDomain() {
        final Path mysqlStore = Path.of("src/main/resources/db/migration/mysql/store/V1__store_foundation.sql");
        final Path sqliteStore = Path.of("src/main/resources/db/migration/sqlite/store/V1__store_foundation.sql");
        assertTrue(Files.exists(mysqlStore));
        assertTrue(Files.exists(sqliteStore));
        try {
            assertTrue(Files.readString(mysqlStore).contains("${store_prefix}store_schema_version"));
            assertTrue(Files.readString(sqliteStore).contains("${store_prefix}store_schema_version"));
        } catch (final java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void rootLevelStaleEconomyMigrationResidueIsRemoved() {
        assertTrue(Files.notExists(Path.of("src/main/resources/db/migration/mysql/V2__economy_core.sql")));
        assertTrue(Files.notExists(Path.of("src/main/resources/db/migration/sqlite/V2__economy_core.sql")));
    }
}
