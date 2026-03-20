# wild_economy P0 persistence patch set

Stage: **P0 partial**

This patch set applies the locked database-design direction to the current repo shape without rewriting the whole plugin at once.

Included in this slice:

* Hikari-backed `DatabaseProvider`
* in-memory authoritative stock cache in `StockServiceImpl`
* async stock persistence queue with backend-aware worker sizing
* async transaction logging queue
* repository preload method for startup stock cache loading
* `ServiceRegistry` wiring and shutdown flush/close behavior

Not yet included in this slice:

* precomputed browse indexes
* atomic SQL stock mutation/delta updates
* batch flush/snapshot metrics
* deeper buy/sell path rework beyond what the cached `StockService` and async `TransactionLogService` already improve

---

## File: `build.gradle.kts`

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
    implementation("com.zaxxer:HikariCP:5.1.0")
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(
            mapOf(
                "version" to project.version,
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

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/ExchangeStockRepository.java`

```java
package com.splatage.wild_economy.exchange.repository;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Map;

public interface ExchangeStockRepository {

    long getStock(ItemKey itemKey);

    Map<ItemKey, Long> getStocks(Iterable<ItemKey> itemKeys);

    Map<ItemKey, Long> loadAllStocks();

    void incrementStock(ItemKey itemKey, int amount);

    void decrementStock(ItemKey itemKey, int amount);

    void setStock(ItemKey itemKey, long stock);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/repository/mysql/MysqlExchangeStockRepository.java`

```java
package com.splatage.wild_economy.exchange.repository.mysql;

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

public final class MysqlExchangeStockRepository implements ExchangeStockRepository {

    private final DatabaseProvider databaseProvider;

    public MysqlExchangeStockRepository(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    @Override
    public long getStock(final ItemKey itemKey) {
        final String sql = "SELECT stock_count FROM exchange_stock WHERE item_key = ?";
        try (
            Connection connection = this.databaseProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
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
    public Map<ItemKey, Long> loadAllStocks() {
        final String sql = "SELECT item_key, stock_count FROM exchange_stock";
        final Map<ItemKey, Long> result = new LinkedHashMap<>();
        try (
            Connection connection = this.databaseProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                result.put(new ItemKey(resultSet.getString("item_key")), resultSet.getLong("stock_count"));
            }
            return result;
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load exchange stock cache", exception);
        }
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
        final String sql = """
            INSERT INTO exchange_stock (item_key, stock_count, updated_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                stock_count = VALUES(stock_count),
                updated_at = VALUES(updated_at)
            """;
        try (
            Connection connection = this.databaseProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, itemKey.value());
            statement.setLong(2, Math.max(0L, stock));
            statement.setLong(3, Instant.now().getEpochSecond());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to set stock for " + itemKey.value(), exception);
        }
    }

    private void changeStock(final ItemKey itemKey, final int delta) {
        final long current = this.getStock(itemKey);
        final long updated = Math.max(0L, current + delta);
        this.setStock(itemKey, updated);
    }
}
```

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
        final String sql = "SELECT stock_count FROM exchange_stock WHERE item_key = ?";
        try (
            Connection connection = this.databaseProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
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
    public Map<ItemKey, Long> loadAllStocks() {
        final String sql = "SELECT item_key, stock_count FROM exchange_stock";
        final Map<ItemKey, Long> result = new LinkedHashMap<>();
        try (
            Connection connection = this.databaseProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                result.put(new ItemKey(resultSet.getString("item_key")), resultSet.getLong("stock_count"));
            }
            return result;
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load exchange stock cache", exception);
        }
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
        final String sql = """
            INSERT INTO exchange_stock (item_key, stock_count, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(item_key) DO UPDATE SET
                stock_count = excluded.stock_count,
                updated_at = excluded.updated_at
            """;
        try (
            Connection connection = this.databaseProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, itemKey.value());
            statement.setLong(2, Math.max(0L, stock));
            statement.setLong(3, Instant.now().getEpochSecond());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to set stock for " + itemKey.value(), exception);
        }
    }

    private void changeStock(final ItemKey itemKey, final int delta) {
        final long current = this.getStock(itemKey);
        final long updated = Math.max(0L, current + delta);
        this.setStock(itemKey, updated);
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockService.java`

```java
package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public interface StockService {

    StockSnapshot getSnapshot(ItemKey itemKey);

    long getAvailableRoom(ItemKey itemKey);

    void addStock(ItemKey itemKey, int amount);

    void removeStock(ItemKey itemKey, int amount);

    void shutdown();
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.StockState;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.persistence.DatabaseDialect;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StockServiceImpl implements StockService {

    private static final int SQLITE_QUEUE_CAPACITY = 4096;
    private static final int MYSQL_QUEUE_CAPACITY = 8192;

    private final ExchangeStockRepository stockRepository;
    private final ExchangeCatalog exchangeCatalog;
    private final StockStateResolver stockStateResolver;
    private final Logger logger;
    private final Map<ItemKey, Long> stockCache;
    private final Set<ItemKey> dirtyKeys;
    private final ExecutorService persistenceExecutor;

    public StockServiceImpl(
        final ExchangeStockRepository stockRepository,
        final ExchangeCatalog exchangeCatalog,
        final StockStateResolver stockStateResolver,
        final Logger logger,
        final DatabaseDialect dialect,
        final int mysqlMaximumPoolSize
    ) {
        this.stockRepository = Objects.requireNonNull(stockRepository, "stockRepository");
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockStateResolver = Objects.requireNonNull(stockStateResolver, "stockStateResolver");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.stockCache = new ConcurrentHashMap<>();
        this.dirtyKeys = ConcurrentHashMap.newKeySet();
        this.persistenceExecutor = this.createExecutor(dialect, mysqlMaximumPoolSize);
        this.preloadCache();
    }

    @Override
    public StockSnapshot getSnapshot(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final long stockCount = this.stockCache.getOrDefault(itemKey, 0L);
        final long stockCap = Math.max(0L, entry.stockCap());
        final double fillRatio = stockCap <= 0L
            ? 0.0D
            : Math.min(1.0D, (double) stockCount / (double) stockCap);
        final StockState stockState = this.stockStateResolver.resolve(stockCount, stockCap);

        return new StockSnapshot(itemKey, stockCount, stockCap, fillRatio, stockState);
    }

    @Override
    public long getAvailableRoom(final ItemKey itemKey) {
        final StockSnapshot snapshot = this.getSnapshot(itemKey);
        if (snapshot.stockCap() <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, snapshot.stockCap() - snapshot.stockCount());
    }

    @Override
    public void addStock(final ItemKey itemKey, final int amount) {
        if (amount <= 0) {
            return;
        }
        this.stockCache.merge(itemKey, (long) amount, Long::sum);
        this.markDirty(itemKey);
    }

    @Override
    public void removeStock(final ItemKey itemKey, final int amount) {
        if (amount <= 0) {
            return;
        }
        this.stockCache.compute(itemKey, (key, current) -> {
            final long currentValue = current == null ? 0L : current;
            return Math.max(0L, currentValue - amount);
        });
        this.markDirty(itemKey);
    }

    @Override
    public void shutdown() {
        this.persistenceExecutor.shutdown();
        try {
            if (!this.persistenceExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                this.persistenceExecutor.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.persistenceExecutor.shutdownNow();
        }
        this.flushDirtyKeysSynchronously();
    }

    private void preloadCache() {
        this.stockCache.putAll(this.stockRepository.loadAllStocks());
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.allEntries()) {
            this.stockCache.putIfAbsent(entry.itemKey(), 0L);
        }
    }

    private ExecutorService createExecutor(final DatabaseDialect dialect, final int mysqlMaximumPoolSize) {
        final int workers = dialect == DatabaseDialect.SQLITE
            ? 1
            : Math.max(2, Math.min(4, mysqlMaximumPoolSize));
        final int queueCapacity = dialect == DatabaseDialect.SQLITE
            ? SQLITE_QUEUE_CAPACITY
            : MYSQL_QUEUE_CAPACITY;
        final String threadPrefix = dialect == DatabaseDialect.SQLITE
            ? "wild-economy-stock-sqlite"
            : "wild-economy-stock-mysql";
        final AtomicInteger threadCounter = new AtomicInteger(1);

        return new ThreadPoolExecutor(
            workers,
            workers,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            runnable -> {
                final Thread thread = new Thread(runnable, threadPrefix + "-" + threadCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        );
    }

    private void markDirty(final ItemKey itemKey) {
        this.dirtyKeys.add(itemKey);
        try {
            this.persistenceExecutor.submit(() -> this.flushDirtyItem(itemKey));
        } catch (final RejectedExecutionException exception) {
            this.logger.log(
                Level.WARNING,
                "Stock persistence queue rejected update for " + itemKey.value() + "; state will remain dirty until shutdown flush.",
                exception
            );
        }
    }

    private void flushDirtyItem(final ItemKey itemKey) {
        final long valueToPersist = Math.max(0L, this.stockCache.getOrDefault(itemKey, 0L));
        try {
            this.stockRepository.setStock(itemKey, valueToPersist);
            final long currentValue = Math.max(0L, this.stockCache.getOrDefault(itemKey, 0L));
            if (currentValue == valueToPersist) {
                this.dirtyKeys.remove(itemKey);
            } else {
                this.markDirty(itemKey);
            }
        } catch (final RuntimeException exception) {
            this.logger.log(
                Level.WARNING,
                "Failed to persist exchange stock for " + itemKey.value() + ". Keeping item dirty for a later flush.",
                exception
            );
        }
    }

    private void flushDirtyKeysSynchronously() {
        for (final ItemKey itemKey : Set.copyOf(this.dirtyKeys)) {
            try {
                this.stockRepository.setStock(itemKey, Math.max(0L, this.stockCache.getOrDefault(itemKey, 0L)));
                this.dirtyKeys.remove(itemKey);
            } catch (final RuntimeException exception) {
                this.logger.log(
                    Level.SEVERE,
                    "Failed to flush dirty exchange stock for " + itemKey.value() + " during shutdown.",
                    exception
                );
            }
        }
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/TransactionLogService.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.UUID;

public interface TransactionLogService {

    void logSale(UUID playerId, ItemKey itemKey, int amount, BigDecimal unitPrice, BigDecimal totalValue);

    void logPurchase(UUID playerId, ItemKey itemKey, int amount, BigDecimal unitPrice, BigDecimal totalValue);

    void logTurnover(ItemKey itemKey, int amount);

    void shutdown();
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/TransactionLogServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.persistence.DatabaseDialect;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TransactionLogServiceImpl implements TransactionLogService {

    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);
    private static final int SQLITE_QUEUE_CAPACITY = 4096;
    private static final int MYSQL_QUEUE_CAPACITY = 8192;

    private final ExchangeTransactionRepository transactionRepository;
    private final Logger logger;
    private final ExecutorService executor;

    public TransactionLogServiceImpl(
        final ExchangeTransactionRepository transactionRepository,
        final Logger logger,
        final DatabaseDialect dialect,
        final int mysqlMaximumPoolSize
    ) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = this.createExecutor(dialect, mysqlMaximumPoolSize);
    }

    @Override
    public void logSale(
        final UUID playerId,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal unitPrice,
        final BigDecimal totalValue
    ) {
        this.submit(
            TransactionType.SELL,
            playerId,
            itemKey,
            amount,
            unitPrice,
            totalValue
        );
    }

    @Override
    public void logPurchase(
        final UUID playerId,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal unitPrice,
        final BigDecimal totalValue
    ) {
        this.submit(
            TransactionType.BUY,
            playerId,
            itemKey,
            amount,
            unitPrice,
            totalValue
        );
    }

    @Override
    public void logTurnover(final ItemKey itemKey, final int amount) {
        this.submit(
            TransactionType.TURNOVER,
            SYSTEM_UUID,
            itemKey,
            amount,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }

    @Override
    public void shutdown() {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.executor.shutdownNow();
        }
    }

    private void submit(
        final TransactionType type,
        final UUID playerId,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal unitPrice,
        final BigDecimal totalValue
    ) {
        try {
            this.executor.submit(() -> this.transactionRepository.insert(
                type,
                playerId,
                itemKey.value(),
                amount,
                unitPrice,
                totalValue,
                Instant.now(),
                null
            ));
        } catch (final RejectedExecutionException exception) {
            this.logger.log(
                Level.WARNING,
                "Transaction log queue rejected " + type + " for " + itemKey.value() + ".",
                exception
            );
        }
    }

    private ExecutorService createExecutor(final DatabaseDialect dialect, final int mysqlMaximumPoolSize) {
        final int workers = dialect == DatabaseDialect.SQLITE
            ? 1
            : Math.max(2, Math.min(4, mysqlMaximumPoolSize));
        final int queueCapacity = dialect == DatabaseDialect.SQLITE
            ? SQLITE_QUEUE_CAPACITY
            : MYSQL_QUEUE_CAPACITY;
        final String threadPrefix = dialect == DatabaseDialect.SQLITE
            ? "wild-economy-txlog-sqlite"
            : "wild-economy-txlog-mysql";
        final AtomicInteger threadCounter = new AtomicInteger(1);

        return new ThreadPoolExecutor(
            workers,
            workers,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            runnable -> {
                final Thread thread = new Thread(runnable, threadPrefix + "-" + threadCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        );
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/persistence/DatabaseProvider.java`

```java
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
```

## File: `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

```java
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.command.ShopOpenSubcommand;
import com.splatage.wild_economy.command.ShopSellAllSubcommand;
import com.splatage.wild_economy.command.ShopSellHandSubcommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.VaultEconomyGateway;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.CatalogMergeService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.GeneratedCatalogImporter;
import com.splatage.wild_economy.exchange.catalog.RootValueImporter;
import com.splatage.wild_economy.exchange.item.BukkitItemNormalizer;
import com.splatage.wild_economy.exchange.item.CanonicalItemRules;
import com.splatage.wild_economy.exchange.item.ItemNormalizer;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.item.ItemValidationServiceImpl;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.pricing.PricingServiceImpl;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlSchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteSchemaVersionRepository;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseService;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeBuyService;
import com.splatage.wild_economy.exchange.service.ExchangeBuyServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeSellService;
import com.splatage.wild_economy.exchange.service.ExchangeSellServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.exchange.service.ExchangeServiceImpl;
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import com.splatage.wild_economy.exchange.service.TransactionLogServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.exchange.stock.StockServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockStateResolver;
import com.splatage.wild_economy.exchange.stock.StockTurnoverService;
import com.splatage.wild_economy.exchange.stock.StockTurnoverServiceImpl;
import com.splatage.wild_economy.gui.ExchangeBrowseMenu;
import com.splatage.wild_economy.gui.ExchangeItemDetailMenu;
import com.splatage.wild_economy.gui.ExchangeRootMenu;
import com.splatage.wild_economy.gui.ExchangeSubcategoryMenu;
import com.splatage.wild_economy.gui.ShopMenuListener;
import com.splatage.wild_economy.gui.ShopMenuRouter;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import java.io.File;
import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
    private ExchangeItemsConfig exchangeItemsConfig;

    private DatabaseProvider databaseProvider;

    private ExchangeCatalog exchangeCatalog;
    private ItemNormalizer itemNormalizer;
    private ItemValidationService itemValidationService;

    private ExchangeStockRepository exchangeStockRepository;
    private ExchangeTransactionRepository exchangeTransactionRepository;

    private EconomyGateway economyGateway;
    private StockService stockService;
    private PricingService pricingService;
    private TransactionLogService transactionLogService;
    private StockTurnoverService stockTurnoverService;
    private ExchangeBrowseService exchangeBrowseService;
    private ExchangeBuyService exchangeBuyService;
    private ExchangeSellService exchangeSellService;
    private ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        final ConfigLoader configLoader = new ConfigLoader(this.plugin);

        this.globalConfig = configLoader.loadGlobalConfig();
        this.databaseConfig = configLoader.loadDatabaseConfig();
        this.exchangeItemsConfig = configLoader.loadExchangeItemsConfig();

        this.databaseProvider = new DatabaseProvider(this.databaseConfig);

        final SchemaVersionRepository schemaVersionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(this.databaseProvider);
            case MYSQL -> new MysqlSchemaVersionRepository(this.databaseProvider);
        };

        final MigrationManager migrationManager = new MigrationManager(this.databaseProvider, schemaVersionRepository);
        migrationManager.migrate();

        this.exchangeStockRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(this.databaseProvider);
            case MYSQL -> new MysqlExchangeStockRepository(this.databaseProvider);
        };

        this.exchangeTransactionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(this.databaseProvider);
            case MYSQL -> new MysqlExchangeTransactionRepository(this.databaseProvider);
        };

        final File rootValuesFile = new File(this.plugin.getDataFolder(), "root-values.yml");
        final File generatedCatalogFile = new File(new File(this.plugin.getDataFolder(), "generated"), "generated-catalog.yml");

        if (!generatedCatalogFile.exists()) {
            this.plugin.getLogger().warning(
                "generated/generated-catalog.yml not found. Runtime catalog will fall back to exchange-items.yml overrides only."
            );
        }

        final GeneratedCatalogImporter generatedCatalogImporter = new GeneratedCatalogImporter();
        final RootValueImporter rootValueImporter = new RootValueImporter();
        final CatalogMergeService catalogMergeService = new CatalogMergeService();
        final CatalogLoader catalogLoader = new CatalogLoader(
            generatedCatalogImporter,
            rootValueImporter,
            catalogMergeService
        );

        this.exchangeCatalog = Objects.requireNonNull(
            catalogLoader.load(this.exchangeItemsConfig, rootValuesFile, generatedCatalogFile),
            "exchangeCatalog"
        );

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        this.itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        this.itemValidationService = new ItemValidationServiceImpl(this.itemNormalizer, this.exchangeCatalog);
        this.economyGateway = this.resolveVaultEconomy();

        final StockStateResolver stockStateResolver = new StockStateResolver();
        this.stockService = new StockServiceImpl(
            this.exchangeStockRepository,
            this.exchangeCatalog,
            stockStateResolver,
            this.plugin.getLogger(),
            this.databaseProvider.dialect(),
            this.databaseConfig.mysqlMaximumPoolSize()
        );

        this.pricingService = new PricingServiceImpl(this.exchangeCatalog);
        this.transactionLogService = new TransactionLogServiceImpl(
            this.exchangeTransactionRepository,
            this.plugin.getLogger(),
            this.databaseProvider.dialect(),
            this.databaseConfig.mysqlMaximumPoolSize()
        );

        this.stockTurnoverService = new StockTurnoverServiceImpl(
            this.exchangeCatalog,
            this.stockService,
            this.transactionLogService
        );

        this.exchangeBrowseService = new ExchangeBrowseServiceImpl(this.exchangeCatalog, this.stockService);

        this.exchangeBuyService = new ExchangeBuyServiceImpl(
            this.exchangeCatalog,
            this.itemValidationService,
            this.stockService,
            this.pricingService,
            this.economyGateway,
            this.transactionLogService
        );

        this.exchangeSellService = new ExchangeSellServiceImpl(
            this.exchangeCatalog,
            this.itemValidationService,
            this.stockService,
            this.pricingService,
            this.economyGateway,
            this.transactionLogService
        );

        this.exchangeService = new ExchangeServiceImpl(
            this.exchangeBrowseService,
            this.exchangeBuyService,
            this.exchangeSellService
        );

        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(this.exchangeService);
        final ExchangeSubcategoryMenu subcategoryMenu = new ExchangeSubcategoryMenu(this.exchangeService);
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService);
        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService);

        this.shopMenuRouter = new ShopMenuRouter(rootMenu, subcategoryMenu, browseMenu, itemDetailMenu);

        rootMenu.setShopMenuRouter(this.shopMenuRouter);
        subcategoryMenu.setShopMenuRouter(this.shopMenuRouter);
        browseMenu.setShopMenuRouter(this.shopMenuRouter);
        itemDetailMenu.setShopMenuRouter(this.shopMenuRouter);

        this.plugin.getServer().getPluginManager().registerEvents(
            new ShopMenuListener(rootMenu, subcategoryMenu, browseMenu, itemDetailMenu, this.shopMenuRouter),
            this.plugin
        );
    }

    public void registerCommands() {
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(
                new ShopOpenSubcommand(this.shopMenuRouter),
                new ShopSellHandSubcommand(this.exchangeService),
                new ShopSellAllSubcommand(this.exchangeService)
            ));
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand(this.plugin));
        }
    }

    public void registerTasks() {
        this.plugin.getServer().getScheduler().runTaskTimer(
            this.plugin,
            new com.splatage.wild_economy.scheduler.StockTurnoverTask(this.stockTurnoverService),
            this.globalConfig.turnoverIntervalTicks(),
            this.globalConfig.turnoverIntervalTicks()
        );
    }

    public void shutdown() {
        this.plugin.getServer().getScheduler().cancelTasks(this.plugin);

        if (this.transactionLogService != null) {
            this.transactionLogService.shutdown();
        }
        if (this.stockService != null) {
            this.stockService.shutdown();
        }
        if (this.databaseProvider != null) {
            this.databaseProvider.close();
        }
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration =
            this.plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }

        return new VaultEconomyGateway(registration.getProvider());
    }
}
```
