package com.splatage.wild_economy.persistence;

import com.splatage.wild_economy.config.DatabaseConfig;
import java.util.Map;

public enum MigrationDomain {
    EXCHANGE("exchange", "exchange_prefix"),
    ECONOMY("economy", "economy_prefix");

    private final String resourceDirectoryName;
    private final String placeholderKey;

    MigrationDomain(final String resourceDirectoryName, final String placeholderKey) {
        this.resourceDirectoryName = resourceDirectoryName;
        this.placeholderKey = placeholderKey;
    }

    public String resourceDirectoryName() {
        return this.resourceDirectoryName;
    }

    public String schemaVersionTableName(final DatabaseConfig databaseConfig) {
        return this.tablePrefix(databaseConfig) + "schema_version";
    }

    public Map<String, String> placeholders(final DatabaseConfig databaseConfig) {
        return Map.of(
                "${economy_prefix}", databaseConfig.economyTablePrefix(),
                "${exchange_prefix}", databaseConfig.exchangeTablePrefix()
        );
    }

    private String tablePrefix(final DatabaseConfig databaseConfig) {
        return switch (this) {
            case EXCHANGE -> databaseConfig.exchangeTablePrefix();
            case ECONOMY -> databaseConfig.economyTablePrefix();
        };
    }
}
