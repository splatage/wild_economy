package com.splatage.wild_economy.persistence;

public final class DatabaseProvider {
}
package com.splatage.wild_economy.persistence;

import com.splatage.wild_economy.config.DatabaseConfig;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

public final class DatabaseProvider {

    private final DatabaseDialect dialect;
    private final String jdbcUrl;
    private final Properties properties;

    public DatabaseProvider(final DatabaseConfig config) {
        Objects.requireNonNull(config, "config");

        final String backend = config.backend().toLowerCase();
        if ("sqlite".equals(backend)) {
            this.dialect = DatabaseDialect.SQLITE;
            final Path sqlitePath = Path.of(config.sqliteFile()).toAbsolutePath();
            sqlitePath.getParent().toFile().mkdirs();
            this.jdbcUrl = "jdbc:sqlite:" + sqlitePath;
            this.properties = new Properties();
        } else if ("mysql".equals(backend)) {
            this.dialect = DatabaseDialect.MYSQL;
            this.jdbcUrl = "jdbc:mysql://" + config.mysqlHost() + ":" + config.mysqlPort() + "/" + config.mysqlDatabase()
                + "?useSSL=" + config.mysqlSsl()
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC";
            this.properties = new Properties();
            this.properties.setProperty("user", config.mysqlUsername());
            this.properties.setProperty("password", config.mysqlPassword());
        } else {
            throw new IllegalStateException("Unsupported database backend: " + config.backend());
        }
    }

    public DatabaseDialect dialect() {
        return this.dialect;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(this.jdbcUrl, this.properties);
    }
}
