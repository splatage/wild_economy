package com.splatage.wild_economy.config;

public record DatabaseConfig(
    String backend,
    String sqliteFile,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    boolean mysqlSsl,
    int mysqlMaximumPoolSize,
    String economyTablePrefix,
    String exchangeTablePrefix,
    String storeTablePrefix
) {}
