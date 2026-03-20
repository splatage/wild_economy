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
