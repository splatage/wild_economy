package com.splatage.wild_economy.persistence;

import com.splatage.wild_economy.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class DatabaseProvider implements AutoCloseable {

    private static final long MYSQL_CONNECTION_TIMEOUT_MS = 5_000L;
    private static final long MYSQL_VALIDATION_TIMEOUT_MS = 3_000L;
    private static final long MYSQL_IDLE_TIMEOUT_MS = 600_000L;
    private static final long MYSQL_MAX_LIFETIME_MS = 1_800_000L;
    private static final long MYSQL_KEEPALIVE_TIME_MS = 300_000L;
    private static final int MYSQL_CONNECT_TIMEOUT_MS = 5_000;
    private static final int MYSQL_SOCKET_TIMEOUT_MS = 15_000;
    private static final int MYSQL_PREP_STMT_CACHE_SIZE = 250;
    private static final int MYSQL_PREP_STMT_CACHE_SQL_LIMIT = 2_048;

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

            hikariConfig.setJdbcUrl(buildMysqlJdbcUrl(config));
            hikariConfig.setUsername(config.mysqlUsername());
            hikariConfig.setPassword(config.mysqlPassword());
            hikariConfig.setMaximumPoolSize(Math.max(2, config.mysqlMaximumPoolSize()));
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setConnectionTimeout(MYSQL_CONNECTION_TIMEOUT_MS);
            hikariConfig.setValidationTimeout(MYSQL_VALIDATION_TIMEOUT_MS);
            hikariConfig.setIdleTimeout(MYSQL_IDLE_TIMEOUT_MS);
            hikariConfig.setMaxLifetime(MYSQL_MAX_LIFETIME_MS);
            hikariConfig.setKeepaliveTime(MYSQL_KEEPALIVE_TIME_MS);
            hikariConfig.setPoolName("wild-economy-mysql");

            hikariConfig.addDataSourceProperty("cachePrepStmts", true);
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", MYSQL_PREP_STMT_CACHE_SIZE);
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", MYSQL_PREP_STMT_CACHE_SQL_LIMIT);
            hikariConfig.addDataSourceProperty("useServerPrepStmts", true);
            hikariConfig.addDataSourceProperty("useLocalSessionState", true);
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", true);
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", true);
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", true);
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", true);
            hikariConfig.addDataSourceProperty("maintainTimeStats", false);
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

    private static String buildMysqlJdbcUrl(final DatabaseConfig config) {
        return "jdbc:mysql://"
            + config.mysqlHost()
            + ":"
            + config.mysqlPort()
            + "/"
            + config.mysqlDatabase()
            + "?useSSL="
            + config.mysqlSsl()
            + "&allowPublicKeyRetrieval=true"
            + "&serverTimezone=UTC"
            + "&connectTimeout="
            + MYSQL_CONNECT_TIMEOUT_MS
            + "&socketTimeout="
            + MYSQL_SOCKET_TIMEOUT_MS
            + "&tcpKeepAlive=true";
    }
}
