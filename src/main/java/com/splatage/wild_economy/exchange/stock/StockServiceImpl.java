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
import java.util.concurrent.atomic.AtomicLong;
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
    private final AtomicLong cacheRevision;
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
        this.cacheRevision = new AtomicLong(0L);
        this.preloadCache();
        this.startFlushScheduler();
    }

    @Override
    public StockSnapshot getSnapshot(final ItemKey itemKey) {
        Objects.requireNonNull(itemKey, "itemKey");
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
                .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));
        final long stockCount = Math.max(0L, this.stockCache.getOrDefault(itemKey, 0L));
        final long stockCap = Math.max(0L, entry.stockCap());
        final double fillRatio = stockCap <= 0L ? 0.0D : Math.min(1.0D, (double) stockCount / (double) stockCap);
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
        Objects.requireNonNull(itemKey, "itemKey");
        if (amount <= 0) {
            return;
        }
        this.stockCache.merge(itemKey, (long) amount, Long::sum);
        this.dirtyKeys.add(itemKey);
        this.cacheRevision.incrementAndGet();
    }

    @Override
    public boolean tryConsume(final ItemKey itemKey, final int amount) {
        Objects.requireNonNull(itemKey, "itemKey");
        if (amount <= 0) {
            return true;
        }
        final AtomicBoolean consumed = new AtomicBoolean(false);
        this.stockCache.compute(itemKey, (key, current) -> {
            final long currentValue = current == null ? 0L : current;
            if (currentValue < amount) {
                return currentValue;
            }
            consumed.set(true);
            return currentValue - amount;
        });
        if (consumed.get()) {
            this.dirtyKeys.add(itemKey);
            this.cacheRevision.incrementAndGet();
        }
        return consumed.get();
    }

    @Override
    public int consumeUpTo(final ItemKey itemKey, final int amount) {
        Objects.requireNonNull(itemKey, "itemKey");
        if (amount <= 0) {
            return 0;
        }
        final AtomicInteger consumed = new AtomicInteger(0);
        this.stockCache.compute(itemKey, (key, current) -> {
            final long currentValue = current == null ? 0L : current;
            final int actualConsumed = (int) Math.min(currentValue, amount);
            consumed.set(actualConsumed);
            return currentValue - actualConsumed;
        });
        if (consumed.get() > 0) {
            this.dirtyKeys.add(itemKey);
            this.cacheRevision.incrementAndGet();
        }
        return consumed.get();
    }

    @Override
    public void removeStock(final ItemKey itemKey, final int amount) {
        this.consumeUpTo(itemKey, amount);
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
    public long cacheRevision() {
        return this.cacheRevision.get();
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
    }

    private ThreadPoolExecutor createExecutor(final DatabaseDialect dialect, final int mysqlMaximumPoolSize) {
        final int workers = dialect == DatabaseDialect.SQLITE ? 1 : Math.max(2, Math.min(4, mysqlMaximumPoolSize));
        final int queueCapacity = dialect == DatabaseDialect.SQLITE ? SQLITE_QUEUE_CAPACITY : MYSQL_QUEUE_CAPACITY;
        final String threadPrefix = dialect == DatabaseDialect.SQLITE ? "wild-economy-stock-sqlite" : "wild-economy-stock-mysql";
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
