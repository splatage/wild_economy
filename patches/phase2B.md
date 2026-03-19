# wild_economy — Commit 2B Copy-Ready Files

## Status

This document contains **copy-ready contents** for **Commit 2B**.

Commit 2B scope:

* database bootstrapping
* migration execution
* SQLite repository implementations
* Vault economy bridge
* service wiring update in `ServiceRegistry`
* required build file update for JDBC drivers

This commit intentionally focuses on getting the first persistence/economy backbone working for the sell path.

---

## Important note before using these files

The current starter `build.gradle.kts` from the first scaffold is **not enough** for real SQLite/MySQL runtime use.
JDBC drivers must be included in the built plugin jar.

So Commit 2B should include an update to `build.gradle.kts`.

---

## File: `build.gradle.kts`

Replace the earlier scaffold version with this updated version.

```kotlin
plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.splatage"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("com.mysql:mysql-connector-j:9.0.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(
            mapOf(
                "version" to project.version
            )
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
```

---

## File: `src/main/java/com/splatage/wild_economy/persistence/DatabaseProvider.java`

```java
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
```

---

## File: `src/main/java/com/splatage/wild_economy/persistence/JdbcUtils.java`

```java
package com.splatage.wild_economy.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcUtils {

    private JdbcUtils() {
    }

    public static void closeQuietly(final AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (final Exception ignored) {
        }
    }

    public static boolean hasColumn(final ResultSet resultSet, final String columnName) throws SQLException {
        final var meta = resultSet.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (columnName.equalsIgnoreCase(meta.getColumnName(i))) {
                return true;
            }
        }
        return false;
    }

    public static void bindNullableString(final PreparedStatement statement, final int index, final String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/persistence/MigrationManager.java`

```java
package com.splatage.wild_economy.persistence;

import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigrationManager {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V(\\d+)__.*\\.sql");

    private final DatabaseProvider databaseProvider;
    private final SchemaVersionRepository schemaVersionRepository;

    public MigrationManager(
        final DatabaseProvider databaseProvider,
        final SchemaVersionRepository schemaVersionRepository
    ) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
        this.schemaVersionRepository = Objects.requireNonNull(schemaVersionRepository, "schemaVersionRepository");
    }

    public void migrate() {
        final int currentVersion = this.safeGetCurrentVersion();
        final List<MigrationResource> migrations = this.loadKnownMigrations();
        migrations.sort(Comparator.comparingInt(MigrationResource::version));

        for (final MigrationResource migration : migrations) {
            if (migration.version() <= currentVersion) {
                continue;
            }
            this.applyMigration(migration);
            this.schemaVersionRepository.setCurrentVersion(migration.version());
        }
    }

    private int safeGetCurrentVersion() {
        try {
            return this.schemaVersionRepository.getCurrentVersion();
        } catch (final RuntimeException ignored) {
            return 0;
        }
    }

    private List<MigrationResource> loadKnownMigrations() {
        final String basePath = switch (this.databaseProvider.dialect()) {
            case SQLITE -> "/db/migration/sqlite/";
            case MYSQL -> "/db/migration/mysql/";
        };

        final List<MigrationResource> migrations = new ArrayList<>();
        // v1 only for now
        final String resourceName = "V1__initial_schema.sql";
        final int version = this.extractVersion(resourceName);
        final String sql = this.readResource(basePath + resourceName);
        migrations.add(new MigrationResource(version, resourceName, sql));
        return migrations;
    }

    private void applyMigration(final MigrationResource migration) {
        try (Connection connection = this.databaseProvider.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            for (final String sqlPart : this.splitStatements(migration.sql())) {
                final String trimmed = sqlPart.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
            connection.commit();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to apply migration " + migration.name(), exception);
        }
    }

    private List<String> splitStatements(final String sql) {
        final List<String> parts = new ArrayList<>();
        final String[] split = sql.split(";\\s*(?:\\r?\\n|$)");
        for (final String part : split) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private int extractVersion(final String resourceName) {
        final Matcher matcher = VERSION_PATTERN.matcher(resourceName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Invalid migration name: " + resourceName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String readResource(final String resourcePath) {
        try (InputStream inputStream = this.getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing migration resource: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                final StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to read migration resource: " + resourcePath, exception);
        }
    }

    private record MigrationResource(int version, String name, String sql) {
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/sqlite/SqliteSchemaVersionRepository.java`

```java
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
    public int getCurrentVersion() {
        final String sql = "SELECT version FROM schema_version ORDER BY version DESC LIMIT 1";
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
    public void setCurrentVersion(final int version) {
        final String deleteSql = "DELETE FROM schema_version";
        final String insertSql = "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)";

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
            throw new IllegalStateException("Failed to set schema version to " + version, exception);
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/sqlite/SqliteExchangeStockRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SqliteExchangeStockRepository implements ExchangeStockRepository {

    private final DatabaseProvider databaseProvider;

    public SqliteExchangeStockRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public long getStock(final ItemKey itemKey) {
        this.ensureRowExists(itemKey);
        final String sql = "SELECT stock_count FROM exchange_stock WHERE item_key = ?";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemKey.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("stock_count");
                }
                return 0L;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load stock for " + itemKey.value(), exception);
        }
    }

    @Override
    public Map<ItemKey, Long> getStocks(final Iterable<ItemKey> itemKeys) {
        final Map<ItemKey, Long> result = new LinkedHashMap<>();
        for (final ItemKey itemKey : itemKeys) {
            result.put(itemKey, this.getStock(itemKey));
        }
        return result;
    }

    @Override
    public void incrementStock(final ItemKey itemKey, final int amount) {
        this.changeStock(itemKey, amount);
    }

    @Override
    public void decrementStock(final ItemKey itemKey, final int amount) {
        this.changeStock(itemKey, -amount);
    }

    @Override
    public void setStock(final ItemKey itemKey, final long stock) {
        this.ensureRowExists(itemKey);
        final String sql = "UPDATE exchange_stock SET stock_count = ?, updated_at = ? WHERE item_key = ?";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Math.max(0L, stock));
            statement.setLong(2, Instant.now().getEpochSecond());
            statement.setString(3, itemKey.value());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to set stock for " + itemKey.value(), exception);
        }
    }

    private void changeStock(final ItemKey itemKey, final int delta) {
        this.ensureRowExists(itemKey);
        final long current = this.getStock(itemKey);
        final long updated = Math.max(0L, current + delta);
        this.setStock(itemKey, updated);
    }

    private void ensureRowExists(final ItemKey itemKey) {
        final String sql = "INSERT OR IGNORE INTO exchange_stock (item_key, stock_count, updated_at) VALUES (?, ?, ?)";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemKey.value());
            statement.setLong(2, 0L);
            statement.setLong(3, Instant.now().getEpochSecond());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to ensure stock row for " + itemKey.value(), exception);
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/sqlite/SqliteExchangeTransactionRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.JdbcUtils;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SqliteExchangeTransactionRepository implements ExchangeTransactionRepository {

    private final DatabaseProvider databaseProvider;

    public SqliteExchangeTransactionRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public void insert(
        final TransactionType type,
        final UUID playerId,
        final String itemKey,
        final int amount,
        final BigDecimal unitPrice,
        final BigDecimal totalValue,
        final Instant createdAt,
        final String metaJson
    ) {
        final String sql = "INSERT INTO exchange_transactions "
            + "(transaction_type, player_uuid, item_key, amount, unit_price, total_value, created_at, meta_json) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, playerId.toString());
            statement.setString(3, itemKey);
            statement.setInt(4, amount);
            statement.setBigDecimal(5, unitPrice);
            statement.setBigDecimal(6, totalValue);
            statement.setLong(7, createdAt.getEpochSecond());
            JdbcUtils.bindNullableString(statement, 8, metaJson);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to insert exchange transaction", exception);
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/economy/VaultEconomyGateway.java`

```java
package com.splatage.wild_economy.economy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class VaultEconomyGateway implements EconomyGateway {

    private final Economy economy;

    public VaultEconomyGateway(final Economy economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    @Override
    public EconomyResult deposit(final UUID playerId, final BigDecimal amount) {
        final OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        final EconomyResponse response = this.economy.depositPlayer(player, amount.doubleValue());
        return new EconomyResult(response.transactionSuccess(), response.errorMessage == null ? "" : response.errorMessage);
    }

    @Override
    public EconomyResult withdraw(final UUID playerId, final BigDecimal amount) {
        final OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        final EconomyResponse response = this.economy.withdrawPlayer(player, amount.doubleValue());
        return new EconomyResult(response.transactionSuccess(), response.errorMessage == null ? "" : response.errorMessage);
    }

    @Override
    public BigDecimal getBalance(final UUID playerId) {
        final OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return BigDecimal.valueOf(this.economy.getBalance(player));
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

Replace the earlier scaffold contents with this Commit 2B wiring version.

```java
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.VaultEconomyGateway;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.CatalogMergeService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.WorthImporter;
import com.splatage.wild_economy.exchange.item.BukkitItemNormalizer;
import com.splatage.wild_economy.exchange.item.CanonicalItemRules;
import com.splatage.wild_economy.exchange.item.ItemNormalizer;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.item.ItemValidationServiceImpl;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteSchemaVersionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
    private WorthImportConfig worthImportConfig;
    private ExchangeItemsConfig exchangeItemsConfig;

    private DatabaseProvider databaseProvider;
    private ExchangeCatalog exchangeCatalog;
    private ItemNormalizer itemNormalizer;
    private ItemValidationService itemValidationService;
    private ExchangeStockRepository exchangeStockRepository;
    private ExchangeTransactionRepository exchangeTransactionRepository;
    private EconomyGateway economyGateway;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        final ConfigLoader configLoader = new ConfigLoader(this.plugin);
        this.globalConfig = configLoader.loadGlobalConfig();
        this.databaseConfig = configLoader.loadDatabaseConfig();
        this.worthImportConfig = configLoader.loadWorthImportConfig();
        this.exchangeItemsConfig = configLoader.loadExchangeItemsConfig();

        this.databaseProvider = new DatabaseProvider(this.databaseConfig);

        final SchemaVersionRepository schemaVersionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL wiring not implemented in Commit 2B");
        };

        final MigrationManager migrationManager = new MigrationManager(this.databaseProvider, schemaVersionRepository);
        migrationManager.migrate();

        this.exchangeStockRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL stock repository not implemented in Commit 2B");
        };

        this.exchangeTransactionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL transaction repository not implemented in Commit 2B");
        };

        final WorthImporter worthImporter = new WorthImporter();
        final CatalogMergeService catalogMergeService = new CatalogMergeService();
        final CatalogLoader catalogLoader = new CatalogLoader(worthImporter, catalogMergeService);
        this.exchangeCatalog = catalogLoader.load(this.exchangeItemsConfig, this.worthImportConfig);

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        this.itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        this.itemValidationService = new ItemValidationServiceImpl(this.itemNormalizer, this.exchangeCatalog);

        this.economyGateway = this.resolveVaultEconomy();
    }

    public void registerCommands() {
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand());
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand());
        }
    }

    public void registerTasks() {
        // Turnover task wiring comes later.
    }

    public void shutdown() {
        // No pooled resources yet in Commit 2B.
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }
        return new VaultEconomyGateway(registration.getProvider());
    }
}
```

---

## Commit 2B outcomes

After these files are in place, the project should be able to:

* load configs
* build a real internal catalog
* normalize/validate sell items
* initialize SQLite storage
* run V1 migrations
* resolve Vault economy
* expose stock and transaction persistence building blocks

What is still missing after Commit 2B:

* pricing implementation
* stock snapshot/state service implementation
* sell service orchestration
* actual `/shop sellhand` and `/shop sellall` behavior

That work belongs to Commit 2C.

---

## Recommended next artifact

The best next artifact is:

**Commit 2C copy-ready files** for:

* `StockServiceImpl.java`
* `StockStateResolver.java`
* `PricingServiceImpl.java`
* `TransactionLogServiceImpl.java`
* `ExchangeSellServiceImpl.java`
* `ExchangeServiceImpl.java`
* `ShopCommand.java`
* `ShopSellHandSubcommand.java`
* `ShopSellAllSubcommand.java`

That will complete the first working sell path.
