package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlSchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteSchemaVersionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationDomain;
import com.splatage.wild_economy.persistence.MigrationManager;

final class MigrationBootstrap {

    private MigrationBootstrap() {
    }

    static SchemaVersionRepository createSchemaVersionRepository(final DatabaseProvider databaseProvider) {
        return switch (databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(databaseProvider);
            case MYSQL -> new MysqlSchemaVersionRepository(databaseProvider);
        };
    }

    static void migrateAll(
            final DatabaseProvider databaseProvider,
            final DatabaseConfig databaseConfig,
            final SchemaVersionRepository schemaVersionRepository
    ) {
        new MigrationManager(
                databaseProvider,
                databaseConfig,
                schemaVersionRepository,
                MigrationDomain.EXCHANGE
        ).migrate();

        new MigrationManager(
                databaseProvider,
                databaseConfig,
                schemaVersionRepository,
                MigrationDomain.ECONOMY
        ).migrate();

        new MigrationManager(
                databaseProvider,
                databaseConfig,
                schemaVersionRepository,
                MigrationDomain.STORE
        ).migrate();
    }
}
