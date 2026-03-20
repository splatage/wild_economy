# wild_economy P2 batched flush / metrics patch set

Stage: **P2**

This patch set applies the locked P2 hardening slice on top of the tested P0/P1 shape.

Included in this slice:

* atomic SQL stock increment/decrement in both repositories
* batched stock flush API on `ExchangeStockRepository`
* batched async persistence in `StockServiceImpl`
* internal periodic flush scheduler in `StockServiceImpl`
* clean shutdown flush in `StockServiceImpl`
* lightweight stock persistence metrics via `StockMetricsSnapshot`

Not included in this slice:

* admin/status command exposure for the metrics
* browse result memoization
* transaction-log batching changes beyond P0
* external config for flush interval / batch size

Notes:

* This patch is intentionally based on the tested P0/P1 source-of-truth you already applied locally.
* It keeps the P2 surface area focused on stock persistence hardening.
* Flush interval and batch size are currently internal constants to keep this pass smaller and lower risk.

---

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

    void flushStocks(Map<ItemKey, Long> stockByItemKey);
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
        if (amount <= 0) {
            return;
        }
        this.changeStockAtomic(itemKey, amount);
    }

    @Override
    public void decrementStock(final ItemKey itemKey, final int amount) {
        if (amount <= 0) {
            return;
        }
        this.changeStockAtomic(itemKey, -amount);
    }

    @Override
    public void setStock(final ItemKey itemKey, final long stock) {
        this.flushStocks(Map.of(itemKey, stock));
    }

    @Override
    public void flushStocks(final Map<ItemKey, Long> stockByItemKey) {
        if (stockByItemKey.isEmpty()) {
            return;
        }

        final String sql = """
            INSERT INTO exchange_stock (item_key, stock_count, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(item_key) DO UPDATE SET
                stock_count = excluded.stock_count,
                updated_at = excluded.updated_at
            """;

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                final long updatedAt = Instant.now().getEpochSecond();
                for (final Map.Entry<ItemKey, Long> entry : stockByItemKey.entrySet()) {
                    statement.setString(1, entry.getKey().value());
                    statement.setLong(2, Math.max(0L, entry.getValue()));
                    statement.setLong(3, updatedAt);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to batch flush exchange stock", exception);
        }
    }

    private void changeStockAtomic(final ItemKey itemKey, final int delta) {
        final String insertSql = "INSERT OR IGNORE INTO exchange_stock (item_key, stock_count, updated_at) VALUES (?, ?, ?)";
        final String updateSql = "UPDATE exchange_stock SET stock_count = MAX(0, stock_count + ?), updated_at = ? WHERE item_key = ?";

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (
                PreparedStatement insertStatement = connection.prepareStatement(insertSql);
                PreparedStatement updateStatement = connection.prepareStatement(updateSql)
            ) {
                final long updatedAt = Instant.now().getEpochSecond();

                insertStatement.setString(1, itemKey.value());
                insertStatement.setLong(2, 0L);
                insertStatement.setLong(3, updatedAt);
                insertStatement.executeUpdate();

                updateStatement.setInt(1, delta);
                updateStatement.setLong(2, updatedAt);
                updateStatement.setString(3, itemKey.value());
                updateStatement.executeUpdate();

                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to atomically change stock for " + itemKey.value(), exception);
        }
    }
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
        if (amount <= 0) {
            return;
        }
        this.changeStockAtomic(itemKey, amount);
    }

    @Override
    public void decrementStock(final ItemKey itemKey, final int amount) {
        if (amount <= 0) {
            return;
        }
        this.changeStockAtomic(itemKey, -amount);
    }

    @Override
    public void setStock(final ItemKey itemKey, final long stock) {
        this.flushStocks(Map.of(itemKey, stock));
    }

    @Override
    public void flushStocks(final Map<ItemKey, Long> stockByItemKey) {
        if (stockByItemKey.isEmpty()) {
            return;
        }

        final String sql = """
            INSERT INTO exchange_stock (item_key, stock_count, updated_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                stock_count = VALUES(stock_count),
                updated_at = VALUES(updated_at)
            """;

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                final long updatedAt = Instant.now().getEpochSecond();
                for (final Map.Entry<ItemKey, Long> entry : stockByItemKey.entrySet()) {
                    statement.setString(1, entry.getKey().value());
                    statement.setLong(2, Math.max(0L, entry.getValue()));
                    statement.setLong(3, updatedAt);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to batch flush exchange stock", exception);
        }
    }

    private void changeStockAtomic(final ItemKey itemKey, final int delta) {
        final String insertSql = "INSERT IGNORE INTO exchange_stock (item_key, stock_count, updated_at) VALUES (?, ?, ?)";
        final String updateSql = "UPDATE exchange_stock SET stock_count = GREATEST(0, stock_count + ?), updated_at = ? WHERE item_key = ?";

        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (
                PreparedStatement insertStatement = connection.prepareStatement(insertSql);
                PreparedStatement updateStatement = connection.prepareStatement(updateSql)
            ) {
                final long updatedAt = Instant.now().getEpochSecond();

                insertStatement.setString(1, itemKey.value());
                insertStatement.setLong(2, 0L);
                insertStatement.setLong(3, updatedAt);
                insertStatement.executeUpdate();

                updateStatement.setInt(1, delta);
                updateStatement.setLong(2, updatedAt);
                updateStatement.setString(3, itemKey.value());
                updateStatement.executeUpdate();

                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to atomically change stock for " + itemKey.value(), exception);
        }
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

    void flushDirtyNow();

    StockMetricsSnapshot metricsSnapshot();

    void shutdown();
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockMetricsSnapshot.java`

```java
package com.splatage.wild_economy.exchange.stock;

public record StockMetricsSnapshot(
    int dirtyItemCount,
    int queuedPersistenceTasks,
    boolean flushInProgress,
    long lastFlushDurationMillis,
    int lastFlushItemCount,
    long totalFlushedItems,
    long totalFlushOperations,
    long totalFlushFailures
) {
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StockServiceImpl implements StockService {

    private static final int SQLITE_QUEUE_CAPACITY = 4096;
    private static final int MYSQL_QUEUE_CAPACITY = 8192;
    private static final int MAX_BATCH_SIZE = 256;
    private static final long FLUSH_INTERVAL_MILLIS = 5000L;

    private final ExchangeStockRepository stockRepository;
    private final ExchangeCatalog exchangeCatalog;
    private final StockStateResolver stockStateResolver;
    private final Logger logger;
    private final Map<ItemKey, Long> stockCache;
    private final Set<ItemKey> dirtyKeys;
    private final ThreadPoolExecutor persistenceExecutor;
    private final ScheduledExecutorService flushScheduler;
    private final AtomicBoolean flushDispatchInProgress;
    private final AtomicInteger pendingBatchCount;
    private final LongAdder totalFlushedItems;
    private final LongAdder totalFlushOperations;
    private final LongAdder totalFlushFailures;

    private volatile long lastFlushDurationMillis;
    private volatile int lastFlushItemCount;

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
        this.flushScheduler = this.createFlushScheduler(dialect);
        this.flushDispatchInProgress = new AtomicBoolean(false);
        this.pendingBatchCount = new AtomicInteger(0);
        this.totalFlushedItems = new LongAdder();
        this.totalFlushOperations = new LongAdder();
        this.totalFlushFailures = new LongAdder();
        this.preloadCache();
        this.startFlushScheduler();
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
        this.dirtyKeys.add(itemKey);
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
        this.dirtyKeys.add(itemKey);
    }

    @Override
    public void flushDirtyNow() {
        if (this.dirtyKeys.isEmpty()) {
            return;
        }
        if (!this.flushDispatchInProgress.compareAndSet(false, true)) {
            return;
        }

        final List<List<ItemKey>> batches = this.snapshotDirtyBatches();
        if (batches.isEmpty()) {
            this.flushDispatchInProgress.set(false);
            return;
        }

        this.pendingBatchCount.set(batches.size());
        for (final List<ItemKey> batch : batches) {
            try {
                this.persistenceExecutor.submit(() -> this.flushBatch(batch));
            } catch (final RejectedExecutionException exception) {
                this.totalFlushFailures.increment();
                this.logger.log(
                    Level.WARNING,
                    "Stock persistence queue rejected a flush batch. Dirty stock will remain pending for a later flush.",
                    exception
                );
                if (this.pendingBatchCount.decrementAndGet() == 0) {
                    this.flushDispatchInProgress.set(false);
                }
            }
        }
    }

    @Override
    public StockMetricsSnapshot metricsSnapshot() {
        return new StockMetricsSnapshot(
            this.dirtyKeys.size(),
            this.persistenceExecutor.getQueue().size(),
            this.flushDispatchInProgress.get(),
            this.lastFlushDurationMillis,
            this.lastFlushItemCount,
            this.totalFlushedItems.sum(),
            this.totalFlushOperations.sum(),
            this.totalFlushFailures.sum()
        );
    }

    @Override
    public void shutdown() {
        this.flushScheduler.shutdown();
        try {
            if (!this.flushScheduler.awaitTermination(5L, TimeUnit.SECONDS)) {
                this.flushScheduler.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.flushScheduler.shutdownNow();
        }

        this.flushDirtyNow();
        this.persistenceExecutor.shutdown();
        try {
            if (!this.persistenceExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                this.persistenceExecutor.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.persistenceExecutor.shutdownNow();
        }

        this.flushDirtySynchronously();
        this.logShutdownSummary();
    }

    private void preloadCache() {
        this.stockCache.putAll(this.stockRepository.loadAllStocks());
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.allEntries()) {
            this.stockCache.putIfAbsent(entry.itemKey(), 0L);
        }
    }

    private ThreadPoolExecutor createExecutor(final DatabaseDialect dialect, final int mysqlMaximumPoolSize) {
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

    private ScheduledExecutorService createFlushScheduler(final DatabaseDialect dialect) {
        final String threadName = dialect == DatabaseDialect.SQLITE
            ? "wild-economy-stock-flush-sqlite"
            : "wild-economy-stock-flush-mysql";

        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    private void startFlushScheduler() {
        this.flushScheduler.scheduleAtFixedRate(
            this::safePeriodicFlush,
            FLUSH_INTERVAL_MILLIS,
            FLUSH_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS
        );
    }

    private void safePeriodicFlush() {
        try {
            this.flushDirtyNow();
        } catch (final RuntimeException exception) {
            this.logger.log(Level.WARNING, "Periodic exchange stock flush failed.", exception);
        }
    }

    private List<List<ItemKey>> snapshotDirtyBatches() {
        final List<ItemKey> snapshot = new ArrayList<>(this.dirtyKeys);
        if (snapshot.isEmpty()) {
            return List.of();
        }

        final List<List<ItemKey>> batches = new ArrayList<>((snapshot.size() + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE);
        for (int index = 0; index < snapshot.size(); index += MAX_BATCH_SIZE) {
            final int end = Math.min(snapshot.size(), index + MAX_BATCH_SIZE);
            batches.add(List.copyOf(snapshot.subList(index, end)));
        }
        return batches;
    }

    private void flushBatch(final List<ItemKey> batch) {
        final Map<ItemKey, Long> snapshot = this.buildFlushSnapshot(batch);
        if (snapshot.isEmpty()) {
            if (this.pendingBatchCount.decrementAndGet() == 0) {
                this.flushDispatchInProgress.set(false);
            }
            return;
        }

        final long startedAt = System.nanoTime();
        try {
            this.stockRepository.flushStocks(snapshot);
            final long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            this.recordSuccessfulFlush(snapshot, durationMillis);
        } catch (final RuntimeException exception) {
            this.totalFlushFailures.increment();
            this.logger.log(Level.WARNING, "Failed to batch flush exchange stock.", exception);
        } finally {
            if (this.pendingBatchCount.decrementAndGet() == 0) {
                this.flushDispatchInProgress.set(false);
            }
        }
    }

    private Map<ItemKey, Long> buildFlushSnapshot(final List<ItemKey> batch) {
        final Map<ItemKey, Long> snapshot = new LinkedHashMap<>(batch.size());
        for (final ItemKey itemKey : batch) {
            snapshot.put(itemKey, Math.max(0L, this.stockCache.getOrDefault(itemKey, 0L)));
        }
        return snapshot;
    }

    private void recordSuccessfulFlush(final Map<ItemKey, Long> snapshot, final long durationMillis) {
        this.lastFlushDurationMillis = durationMillis;
        this.lastFlushItemCount = snapshot.size();
        this.totalFlushOperations.increment();
        this.totalFlushedItems.add(snapshot.size());

        for (final Map.Entry<ItemKey, Long> entry : snapshot.entrySet()) {
            final long currentValue = Math.max(0L, this.stockCache.getOrDefault(entry.getKey(), 0L));
            if (currentValue == entry.getValue()) {
                this.dirtyKeys.remove(entry.getKey());
            }
        }
    }

    private void flushDirtySynchronously() {
        while (!this.dirtyKeys.isEmpty()) {
            final List<List<ItemKey>> batches = this.snapshotDirtyBatches();
            if (batches.isEmpty()) {
                return;
            }

            for (final List<ItemKey> batch : batches) {
                final Map<ItemKey, Long> snapshot = this.buildFlushSnapshot(batch);
                if (snapshot.isEmpty()) {
                    continue;
                }
                try {
                    final long startedAt = System.nanoTime();
                    this.stockRepository.flushStocks(snapshot);
                    final long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                    this.recordSuccessfulFlush(snapshot, durationMillis);
                } catch (final RuntimeException exception) {
                    this.totalFlushFailures.increment();
                    this.logger.log(Level.SEVERE, "Failed to synchronously flush exchange stock during shutdown.", exception);
                    return;
                }
            }
        }
    }

    private void logShutdownSummary() {
        final StockMetricsSnapshot metrics = this.metricsSnapshot();
        this.logger.info(
            "Exchange stock persistence summary: flushedItems=" + metrics.totalFlushedItems()
                + ", flushOperations=" + metrics.totalFlushOperations()
                + ", flushFailures=" + metrics.totalFlushFailures()
                + ", dirtyRemaining=" + metrics.dirtyItemCount()
                + ", queueDepth=" + metrics.queuedPersistenceTasks()
        );
    }
}
```
