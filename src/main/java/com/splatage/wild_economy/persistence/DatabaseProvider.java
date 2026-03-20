package com.splatage.wild_economy.persistence;

import com.splatage.wild_economy.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class DatabaseProvider implements AutoCloseable {

    private final DatabaseDialect dialect;
    private final HikariDataSource dataSource;

    public DatabaseProvider(final DatabaseConfig config) {
        Objects.requireNonNull(config, "config");

        final String backend = config.backend().toLowerCase();
        final HikariConfig hikariConfig = new HikariConfig();

        if ("sqlite".equals(backend)) {
            this.dialect = DatabaseDialect.SQLITE;

            final Path sqlitePath = Path.of(config.sqliteFile()).toAbsolutePath();
            final Path parent = sqlitePath.getParent();
            if (parent != null) {
                parent.toFile().mkdirs();
            }

            hikariConfig.setJdbcUrl("jdbc:sqlite:" + sqlitePath);
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setPoolName("wild-economy-sqlite");
            hikariConfig.setConnectionTestQuery("SELECT 1");
        } else if ("mysql".equals(backend)) {
            this.dialect = DatabaseDialect.MYSQL;

            hikariConfig.setJdbcUrl(
                "jdbc:mysql://"
                    + config.mysqlHost()
                    + ":"
                    + config.mysqlPort()
                    + "/"
                    + config.mysqlDatabase()
                    + "?useSSL="
                    + config.mysqlSsl()
                    + "&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=UTC"
            );
            hikariConfig.setUsername(config.mysqlUsername());
            hikariConfig.setPassword(config.mysqlPassword());
            hikariConfig.setMaximumPoolSize(Math.max(2, config.mysqlMaximumPoolSize()));
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setPoolName("wild-economy-mysql");
        } else {
            throw new IllegalStateException("Unsupported database backend: " + config.backend());
        }

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    public DatabaseDialect dialect() {
        return this.dialect;
    }

    public Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();
    }

    @Override
    public void close() {
        this.dataSource.close();
    }
}
