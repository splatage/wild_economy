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
    void storeDomainUsesStorePrefixForSchemaVersionTable() {
        assertEquals("store_schema_version", MigrationDomain.STORE.schemaVersionTableName(DATABASE_CONFIG));
    }

    @Test
    void storeDomainUsesDedicatedResourceDirectory() {
        assertEquals("store", MigrationDomain.STORE.resourceDirectoryName());
    }

    @Test
    void storeFoundationMigrationsExistUnderDedicatedStoreDomain() {
        assertTrue(Files.exists(Path.of("src/main/resources/db/migration/mysql/store/V1__store_foundation.sql")));
        assertTrue(Files.exists(Path.of("src/main/resources/db/migration/sqlite/store/V1__store_foundation.sql")));
    }

    @Test
    void rootLevelStaleEconomyMigrationResidueIsRemoved() {
        assertTrue(Files.notExists(Path.of("src/main/resources/db/migration/mysql/V2__economy_core.sql")));
        assertTrue(Files.notExists(Path.of("src/main/resources/db/migration/sqlite/V2__economy_core.sql")));
    }
}
