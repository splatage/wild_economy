# wild_economy stock correctness + grouped sell pricing patch

This patch keeps the existing memory-first runtime stock model and async persistence/logging architecture, while fixing the main correctness and hot-path issues:

- buy-side stock consumption becomes atomic
- turnover removes and logs the actual amount removed
- sell pricing aggregates by item key before quoting
- sell pricing uses batch-average/trapezoid payout with a cap-floor split
- sell pricing and in-memory stock mutation stay synchronous on the gameplay path
- DB persistence and transaction logging remain asynchronous

---

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockService.java`

```java
package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public interface StockService {

    StockSnapshot getSnapshot(ItemKey itemKey);

    long getAvailableRoom(ItemKey itemKey);

    void addStock(ItemKey itemKey, int amount);

    boolean tryConsume(ItemKey itemKey, int amount);

    int consumeUpTo(ItemKey itemKey, int amount);

    void removeStock(ItemKey itemKey, int amount);

    void flushDirtyNow();

    StockMetricsSnapshot metricsSnapshot();

    void shutdown();
}
```

---

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
        Objects.requireNonNull(itemKey, "itemKey");

        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final long stockCount = Math.max(0L, this.stockCache.getOrDefault(itemKey, 0L));
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
        Objects.requireNonNull(itemKey, "itemKey");
        if (amount <= 0) {
            return;
        }

        this.stockCache.merge(itemKey, (long) amount, Long::sum);
        this.dirtyKeys.add(itemKey);
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

---

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockTurnoverServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import java.util.Objects;

public final class StockTurnoverServiceImpl implements StockTurnoverService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;
    private final TransactionLogService transactionLogService;

    public StockTurnoverServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService,
        final TransactionLogService transactionLogService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.transactionLogService = Objects.requireNonNull(transactionLogService, "transactionLogService");
    }

    @Override
    public void runTurnoverPass() {
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.allEntries()) {
            if (entry.policyMode() != ItemPolicyMode.PLAYER_STOCKED) {
                continue;
            }

            final long turnover = Math.max(0L, entry.turnoverAmountPerInterval());
            if (turnover <= 0L) {
                continue;
            }

            final int requestedRemoval = (int) Math.min(Integer.MAX_VALUE, turnover);
            final int actualRemoved = this.stockService.consumeUpTo(entry.itemKey(), requestedRemoval);
            if (actualRemoved <= 0) {
                continue;
            }

            this.transactionLogService.logTurnover(entry.itemKey(), actualRemoved);
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.EconomyResult;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.item.ValidationResult;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ExchangeBuyServiceImpl implements ExchangeBuyService {

    private static final int MAX_BUY_AMOUNT = 64;

    private final ExchangeCatalog exchangeCatalog;
    private final ItemValidationService itemValidationService;
    private final StockService stockService;
    private final PricingService pricingService;
    private final EconomyGateway economyGateway;
    private final TransactionLogService transactionLogService;

    public ExchangeBuyServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final ItemValidationService itemValidationService,
        final StockService stockService,
        final PricingService pricingService,
        final EconomyGateway economyGateway,
        final TransactionLogService transactionLogService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.itemValidationService = Objects.requireNonNull(itemValidationService, "itemValidationService");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.economyGateway = Objects.requireNonNull(economyGateway, "economyGateway");
        this.transactionLogService = Objects.requireNonNull(transactionLogService, "transactionLogService");
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new BuyResult(false, itemKey, 0, null, null, RejectionReason.INTERNAL_ERROR, "Player is not online");
        }

        if (amount <= 0 || amount > MAX_BUY_AMOUNT) {
            return new BuyResult(
                false,
                itemKey,
                0,
                null,
                null,
                RejectionReason.BUY_NOT_ALLOWED,
                "Amount must be between 1 and " + MAX_BUY_AMOUNT
            );
        }

        final ValidationResult validation = this.itemValidationService.validateForBuy(itemKey);
        if (!validation.valid()) {
            return new BuyResult(false, itemKey, 0, null, null, validation.rejectionReason(), validation.detail());
        }

        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final boolean playerStocked = entry.policyMode() == ItemPolicyMode.PLAYER_STOCKED;
        final StockSnapshot snapshot = this.stockService.getSnapshot(itemKey);
        if (playerStocked && snapshot.stockCount() < amount) {
            return new BuyResult(false, itemKey, 0, null, null, RejectionReason.OUT_OF_STOCK, "Not enough stock available");
        }

        final BuyQuote quote = this.pricingService.quoteBuy(itemKey, amount, snapshot);
        final BigDecimal balance = this.economyGateway.getBalance(playerId);
        if (balance.compareTo(quote.totalPrice()) < 0) {
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.INSUFFICIENT_FUNDS,
                "Not enough money"
            );
        }

        final Material material = Material.matchMaterial(itemKey.value().replace("minecraft:", "").toUpperCase());
        if (material == null || material == Material.AIR) {
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.INTERNAL_ERROR,
                "Invalid material mapping"
            );
        }

        final ItemStack toGive = new ItemStack(material, amount);
        if (!this.canFit(player.getInventory(), toGive)) {
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.INVENTORY_FULL,
                "Not enough inventory space"
            );
        }

        if (playerStocked && !this.stockService.tryConsume(itemKey, amount)) {
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.OUT_OF_STOCK,
                "Not enough stock available"
            );
        }

        final EconomyResult withdrawal = this.economyGateway.withdraw(playerId, quote.totalPrice());
        if (!withdrawal.success()) {
            if (playerStocked) {
                this.stockService.addStock(itemKey, amount);
            }
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.INTERNAL_ERROR,
                withdrawal.message()
            );
        }

        final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);
        final int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        final int amountBought = Math.max(0, amount - leftoverAmount);

        if (leftoverAmount > 0 && playerStocked) {
            this.stockService.addStock(itemKey, leftoverAmount);
        }

        if (amountBought <= 0) {
            this.economyGateway.deposit(playerId, quote.totalPrice());
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                RejectionReason.INVENTORY_FULL,
                "Not enough inventory space"
            );
        }

        final BigDecimal actualTotalPrice = quote.unitPrice()
            .multiply(BigDecimal.valueOf(amountBought))
            .setScale(2, RoundingMode.HALF_UP);

        if (leftoverAmount > 0) {
            final BigDecimal refund = quote.totalPrice().subtract(actualTotalPrice).setScale(2, RoundingMode.HALF_UP);
            if (refund.compareTo(BigDecimal.ZERO) > 0) {
                this.economyGateway.deposit(playerId, refund);
            }
        }

        this.transactionLogService.logPurchase(playerId, itemKey, amountBought, quote.unitPrice(), actualTotalPrice);

        final String message = amountBought == amount
            ? "Bought " + amountBought + "x " + entry.displayName() + " for " + actualTotalPrice
            : "Bought " + amountBought + "x " + entry.displayName() + " for " + actualTotalPrice
                + " (inventory accepted fewer than requested)";

        return new BuyResult(true, itemKey, amountBought, quote.unitPrice(), actualTotalPrice, null, message);
    }

    private boolean canFit(final Inventory inventory, final ItemStack itemStack) {
        int remaining = itemStack.getAmount();
        final int slotMax = Math.min(itemStack.getMaxStackSize(), inventory.getMaxStackSize());

        for (final ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType() == Material.AIR) {
                remaining -= slotMax;
                if (remaining <= 0) {
                    return true;
                }
                continue;
            }

            if (!existing.isSimilar(itemStack)) {
                continue;
            }

            final int existingSlotMax = Math.min(existing.getMaxStackSize(), slotMax);
            final int freeSpace = Math.max(0, existingSlotMax - existing.getAmount());
            remaining -= freeSpace;
            if (remaining <= 0) {
                return true;
            }
        }

        return remaining <= 0;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.pricing;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public final class PricingServiceImpl implements PricingService {

    private static final int MONEY_SCALE = 2;
    private static final int INTERNAL_SCALE = 8;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal TWO = BigDecimal.valueOf(2L);
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);

    private final ExchangeCatalog exchangeCatalog;

    public PricingServiceImpl(final ExchangeCatalog exchangeCatalog) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
    }

    @Override
    public BuyQuote quoteBuy(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final BigDecimal unitPrice = this.nonNullPrice(entry.buyPrice());
        final BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(amount)).setScale(MONEY_SCALE, MONEY_ROUNDING);
        return new BuyQuote(itemKey, amount, unitPrice, totalPrice);
    }

    @Override
    public SellQuote quoteSell(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final BigDecimal baseUnitPrice = this.nonNullPrice(entry.sellPrice());
        if (amount <= 0 || baseUnitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new SellQuote(itemKey, amount, baseUnitPrice, ZERO_MONEY, ZERO_MONEY, stockSnapshot.fillRatio(), false);
        }

        final BigDecimal totalPrice = this.resolveSellTotalPrice(entry, amount, stockSnapshot, baseUnitPrice);
        final BigDecimal effectiveUnitPrice = totalPrice.divide(
            BigDecimal.valueOf(amount),
            MONEY_SCALE,
            MONEY_ROUNDING
        );
        final boolean tapered = effectiveUnitPrice.compareTo(baseUnitPrice) < 0;

        return new SellQuote(
            itemKey,
            amount,
            baseUnitPrice,
            effectiveUnitPrice,
            totalPrice,
            stockSnapshot.fillRatio(),
            tapered
        );
    }

    private BigDecimal resolveSellTotalPrice(
        final ExchangeCatalogEntry entry,
        final int amount,
        final StockSnapshot stockSnapshot,
        final BigDecimal baseUnitPrice
    ) {
        final long startStock = Math.max(0L, stockSnapshot.stockCount());
        final long stockCap = Math.max(0L, stockSnapshot.stockCap());

        if (stockCap <= 0L) {
            final BigDecimal startUnitPrice = this.resolveSellUnitPrice(entry, baseUnitPrice, 0.0D);
            return startUnitPrice.multiply(BigDecimal.valueOf(amount)).setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        final long endStock = startStock + amount;
        final BigDecimal startUnitPrice = this.resolveSellUnitPrice(entry, baseUnitPrice, this.fillRatio(startStock, stockCap));
        final BigDecimal floorUnitPrice = this.resolveSellUnitPrice(entry, baseUnitPrice, 1.0D);

        if (startStock >= stockCap) {
            return floorUnitPrice.multiply(BigDecimal.valueOf(amount)).setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        if (endStock <= stockCap) {
            final BigDecimal endUnitPrice = this.resolveSellUnitPrice(entry, baseUnitPrice, this.fillRatio(endStock, stockCap));
            return this.averageUnitPriceTotal(startUnitPrice, endUnitPrice, amount);
        }

        final long beforeCapAmount = Math.max(0L, stockCap - startStock);
        final long afterCapAmount = Math.max(0L, amount - beforeCapAmount);

        final BigDecimal beforeCapTotal = this.averageUnitPriceTotal(startUnitPrice, floorUnitPrice, beforeCapAmount);
        final BigDecimal afterCapTotal = floorUnitPrice
            .multiply(BigDecimal.valueOf(afterCapAmount))
            .setScale(MONEY_SCALE, MONEY_ROUNDING);

        return beforeCapTotal.add(afterCapTotal).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal averageUnitPriceTotal(
        final BigDecimal startUnitPrice,
        final BigDecimal endUnitPrice,
        final long amount
    ) {
        if (amount <= 0L) {
            return ZERO_MONEY;
        }

        return startUnitPrice
            .add(endUnitPrice)
            .multiply(BigDecimal.valueOf(amount))
            .divide(TWO, INTERNAL_SCALE, MONEY_ROUNDING)
            .setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal resolveSellUnitPrice(
        final ExchangeCatalogEntry entry,
        final BigDecimal baseUnitPrice,
        final double fillRatio
    ) {
        final BigDecimal multiplier = this.resolveSellMultiplier(entry.sellPriceBands(), fillRatio);
        return baseUnitPrice.multiply(multiplier);
    }

    private BigDecimal resolveSellMultiplier(final List<SellPriceBand> bands, final double fillRatio) {
        if (bands == null || bands.isEmpty()) {
            return BigDecimal.ONE;
        }

        for (final SellPriceBand band : bands) {
            if (fillRatio >= band.minFillRatioInclusive() && fillRatio < band.maxFillRatioExclusive()) {
                return this.nonNullMultiplier(band.multiplier());
            }
        }

        final SellPriceBand lastBand = bands.get(bands.size() - 1);
        if (fillRatio >= lastBand.minFillRatioInclusive()) {
            return this.nonNullMultiplier(lastBand.multiplier());
        }

        return BigDecimal.ONE;
    }

    private double fillRatio(final long stockCount, final long stockCap) {
        if (stockCap <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) Math.max(0L, stockCount) / (double) stockCap);
    }

    private BigDecimal nonNullPrice(final BigDecimal price) {
        return price == null ? ZERO_MONEY : price.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal nonNullMultiplier(final BigDecimal multiplier) {
        return multiplier == null ? BigDecimal.ONE : multiplier;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.EconomyResult;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellLineResult;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.item.ValidationResult;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.integration.protection.ContainerAccessResult;
import com.splatage.wild_economy.integration.protection.ContainerAccessService;
import com.splatage.wild_economy.integration.protection.ContainerAccessServices;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Lockable;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public final class ExchangeSellServiceImpl implements ExchangeSellService {

    private static final int CONTAINER_TARGET_RANGE = 5;
    private static final String LOCKED_CONTAINER_MESSAGE = "That container is locked.";

    private final ExchangeCatalog exchangeCatalog;
    private final ItemValidationService itemValidationService;
    private final StockService stockService;
    private final PricingService pricingService;
    private final EconomyGateway economyGateway;
    private final TransactionLogService transactionLogService;
    private final ContainerAccessService containerAccessService;

    public ExchangeSellServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final ItemValidationService itemValidationService,
        final StockService stockService,
        final PricingService pricingService,
        final EconomyGateway economyGateway,
        final TransactionLogService transactionLogService
    ) {
        this(
            exchangeCatalog,
            itemValidationService,
            stockService,
            pricingService,
            economyGateway,
            transactionLogService,
            ContainerAccessServices.createDefault()
        );
    }

    ExchangeSellServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final ItemValidationService itemValidationService,
        final StockService stockService,
        final PricingService pricingService,
        final EconomyGateway economyGateway,
        final TransactionLogService transactionLogService,
        final ContainerAccessService containerAccessService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.itemValidationService = Objects.requireNonNull(itemValidationService, "itemValidationService");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.economyGateway = Objects.requireNonNull(economyGateway, "economyGateway");
        this.transactionLogService = Objects.requireNonNull(transactionLogService, "transactionLogService");
        this.containerAccessService = Objects.requireNonNull(containerAccessService, "containerAccessService");
    }

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, "Player is not online");
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        final ValidationResult validation = this.itemValidationService.validateForSell(held);
        if (!validation.valid()) {
            return new SellHandResult(false, null, validation.rejectionReason(), validation.detail());
        }

        final ItemKey itemKey = validation.itemKey();
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final int amount = held.getAmount();
        final StockSnapshot stockSnapshot = this.stockService.getSnapshot(itemKey);
        final SellQuote quote = this.pricingService.quoteSell(itemKey, amount, stockSnapshot);
        if (quote.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return new SellHandResult(false, null, RejectionReason.SELL_NOT_ALLOWED, "Sell value is zero");
        }

        final ItemStack restoreStack = held.clone();
        player.getInventory().setItemInMainHand(null);

        final EconomyResult payout = this.economyGateway.deposit(playerId, quote.totalPrice());
        if (!payout.success()) {
            player.getInventory().setItemInMainHand(restoreStack);
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, payout.message());
        }

        this.stockService.addStock(itemKey, amount);
        this.transactionLogService.logSale(playerId, itemKey, amount, quote.effectiveUnitPrice(), quote.totalPrice());

        final SellLineResult lineResult = new SellLineResult(
            itemKey,
            entry.displayName(),
            amount,
            quote.effectiveUnitPrice(),
            quote.totalPrice(),
            quote.tapered()
        );

        final String message = quote.tapered()
            ? "Sold " + amount + "x " + entry.displayName() + " for " + quote.totalPrice()
                + " (reduced due to high stock)"
            : "Sold " + amount + "x " + entry.displayName() + " for " + quote.totalPrice();

        return new SellHandResult(true, lineResult, null, message);
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        final SalePlanning planning = this.planSalesFromInventory(
            player.getInventory(),
            true,
            "protected container item; use /sellcontainer"
        );
        if (planning.plannedSales().isEmpty()) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, planning.skippedDescriptions(), "No sellable items found");
        }

        final Inventory inventory = player.getInventory();
        this.removePlannedItems(inventory, planning);

        final EconomyResult payout = this.economyGateway.deposit(playerId, planning.totalEarned());
        if (!payout.success()) {
            this.restorePlannedItems(inventory, planning);
            return new SellAllResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "Payout failed: " + payout.message()
            );
        }

        final List<SellLineResult> soldLines = this.completePlannedSales(playerId, planning.plannedSales());
        String message = "Sold " + soldLines.size() + " item type(s) for a total of " + planning.totalEarned();
        if (planning.taperedAny()) {
            message += " (some items sold at reduced value due to high stock)";
        }

        return new SellAllResult(
            true,
            List.copyOf(soldLines),
            planning.totalEarned(),
            planning.skippedDescriptions(),
            message
        );
    }

    @Override
    public SellContainerResult sellContainer(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), null, "Player is not online");
        }

        final Block targetBlock = player.getTargetBlockExact(CONTAINER_TARGET_RANGE);
        final SupportedContainerTarget blockTarget = this.resolveSupportedBlockTarget(targetBlock);
        if (blockTarget != null) {
            if (this.isLocked(blockTarget.state())) {
                return this.deniedPlacedContainer(blockTarget.description(), LOCKED_CONTAINER_MESSAGE);
            }

            final ContainerAccessResult accessResult = this.canAccessPlacedContainer(player, blockTarget.block());
            if (!accessResult.allowed()) {
                return this.deniedPlacedContainer(blockTarget.description(), accessResult.message());
            }

            return this.sellFromInventoryTarget(playerId, blockTarget.inventory(), blockTarget.description());
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        if (this.isHeldShulkerItem(held)) {
            return this.sellFromHeldShulker(playerId, player, held);
        }

        return new SellContainerResult(
            false,
            List.of(),
            BigDecimal.ZERO,
            List.of(),
            null,
            "No supported container found. Look at a chest, barrel, or shulker, or hold a shulker box."
        );
    }

    boolean isSupportedPlacedContainerTarget(final Block targetBlock) {
        return this.resolveSupportedBlockTarget(targetBlock) != null;
    }

    ContainerAccessResult canAccessPlacedContainer(final Player player, final Block targetBlock) {
        return this.containerAccessService.canAccessPlacedContainer(player, targetBlock);
    }

    SellContainerResult buildPlacedContainerDeniedResult(final Block targetBlock, final String message) {
        return this.deniedPlacedContainer(this.describeTargetBlock(targetBlock), message);
    }

    SellContainerResult sellPlacedContainerAtLocation(final UUID playerId, final Location location) {
        if (location == null || location.getWorld() == null) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                null,
                "No supported container found. It may have changed before the sale completed."
            );
        }

        final SupportedContainerTarget blockTarget = this.resolveSupportedBlockTarget(location.getBlock());
        if (blockTarget == null) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                null,
                "No supported container found. It may have changed before the sale completed."
            );
        }

        if (this.isLocked(blockTarget.state())) {
            return this.deniedPlacedContainer(blockTarget.description(), LOCKED_CONTAINER_MESSAGE);
        }

        return this.sellFromInventoryTarget(playerId, blockTarget.inventory(), blockTarget.description());
    }

    private SellContainerResult sellFromInventoryTarget(
        final UUID playerId,
        final Inventory inventory,
        final String targetDescription
    ) {
        final SalePlanning planning = this.planSalesFromInventory(inventory, true, "nested container not supported");
        if (planning.plannedSales().isEmpty()) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                targetDescription,
                "No sellable items found in " + targetDescription + "."
            );
        }

        this.removePlannedItems(inventory, planning);

        final EconomyResult payout = this.economyGateway.deposit(playerId, planning.totalEarned());
        if (!payout.success()) {
            this.restorePlannedItems(inventory, planning);
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                targetDescription,
                "Payout failed: " + payout.message()
            );
        }

        final List<SellLineResult> soldLines = this.completePlannedSales(playerId, planning.plannedSales());
        String message = "Sold contents of " + targetDescription + " for a total of " + planning.totalEarned();
        if (planning.taperedAny()) {
            message += " (some items sold at reduced value due to high stock)";
        }

        return new SellContainerResult(
            true,
            List.copyOf(soldLines),
            planning.totalEarned(),
            planning.skippedDescriptions(),
            targetDescription,
            message
        );
    }

    private SellContainerResult sellFromHeldShulker(
        final UUID playerId,
        final Player player,
        final ItemStack heldShulker
    ) {
        final ItemStack originalHeld = heldShulker.clone();
        final BlockStateMeta blockStateMeta = (BlockStateMeta) heldShulker.getItemMeta();
        if (blockStateMeta == null || !(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                "held shulker box",
                "Held shulker box is not readable."
            );
        }

        final SalePlanning planning = this.planSalesFromInventory(
            shulkerBox.getInventory(),
            true,
            "nested container not supported"
        );
        if (planning.plannedSales().isEmpty()) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "held shulker box",
                "No sellable items found in held shulker box."
            );
        }

        this.removePlannedItems(shulkerBox.getInventory(), planning);

        final ItemStack updatedHeld = heldShulker.clone();
        final BlockStateMeta updatedMeta = (BlockStateMeta) updatedHeld.getItemMeta();
        if (updatedMeta == null) {
            this.restorePlannedItems(shulkerBox.getInventory(), planning);
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "held shulker box",
                "Failed to update held shulker box contents."
            );
        }

        updatedMeta.setBlockState(shulkerBox);
        updatedHeld.setItemMeta(updatedMeta);
        player.getInventory().setItemInMainHand(updatedHeld);

        final EconomyResult payout = this.economyGateway.deposit(playerId, planning.totalEarned());
        if (!payout.success()) {
            player.getInventory().setItemInMainHand(originalHeld);
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "held shulker box",
                "Payout failed: " + payout.message()
            );
        }

        final List<SellLineResult> soldLines = this.completePlannedSales(playerId, planning.plannedSales());
        String message = "Sold contents of held shulker box for a total of " + planning.totalEarned();
        if (planning.taperedAny()) {
            message += " (some items sold at reduced value due to high stock)";
        }

        return new SellContainerResult(
            true,
            List.copyOf(soldLines),
            planning.totalEarned(),
            planning.skippedDescriptions(),
            "held shulker box",
            message
        );
    }

    private List<SellLineResult> completePlannedSales(
        final UUID playerId,
        final List<GroupedPlannedSale> plannedSales
    ) {
        final List<SellLineResult> soldLines = new ArrayList<>(plannedSales.size());
        for (final GroupedPlannedSale sale : plannedSales) {
            this.stockService.addStock(sale.itemKey(), sale.amount());
            this.transactionLogService.logSale(
                playerId,
                sale.itemKey(),
                sale.amount(),
                sale.quote().effectiveUnitPrice(),
                sale.quote().totalPrice()
            );
            soldLines.add(new SellLineResult(
                sale.itemKey(),
                sale.displayName(),
                sale.amount(),
                sale.quote().effectiveUnitPrice(),
                sale.quote().totalPrice(),
                sale.quote().tapered()
            ));
        }
        return soldLines;
    }

    private SalePlanning planSalesFromInventory(
        final Inventory inventory,
        final boolean protectShulkers,
        final String protectedShulkerReason
    ) {
        final Map<ItemKey, GroupedSaleAccumulator> groupedSales = new LinkedHashMap<>();
        final List<String> skippedDescriptions = new ArrayList<>();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            if (protectShulkers && this.isShulkerBoxItem(stack.getType())) {
                skippedDescriptions.add(
                    this.friendlyMaterialName(stack.getType()) + " x" + stack.getAmount() + " (" + protectedShulkerReason + ")"
                );
                continue;
            }

            final ValidationResult validation = this.itemValidationService.validateForSell(stack);
            if (!validation.valid()) {
                continue;
            }

            final ItemKey itemKey = validation.itemKey();
            final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
                .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

            groupedSales
                .computeIfAbsent(itemKey, key -> new GroupedSaleAccumulator(itemKey, entry.displayName()))
                .add(slot, stack);
        }

        final List<GroupedPlannedSale> plannedSales = new ArrayList<>(groupedSales.size());
        BigDecimal totalEarned = BigDecimal.ZERO;
        boolean taperedAny = false;

        for (final GroupedSaleAccumulator accumulator : groupedSales.values()) {
            final StockSnapshot stockSnapshot = this.stockService.getSnapshot(accumulator.itemKey());
            final SellQuote quote = this.pricingService.quoteSell(
                accumulator.itemKey(),
                accumulator.totalAmount(),
                stockSnapshot
            );
            if (quote.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
                skippedDescriptions.add(accumulator.displayName() + " x" + accumulator.totalAmount() + " (zero value)");
                continue;
            }

            plannedSales.add(accumulator.toPlannedSale(quote));
            totalEarned = totalEarned.add(quote.totalPrice());
            taperedAny |= quote.tapered();
        }

        return new SalePlanning(
            List.copyOf(plannedSales),
            List.copyOf(skippedDescriptions),
            totalEarned.setScale(2, RoundingMode.HALF_UP),
            taperedAny
        );
    }

    private void removePlannedItems(final Inventory inventory, final SalePlanning planning) {
        for (final GroupedPlannedSale sale : planning.plannedSales()) {
            for (final InventoryRemoval removal : sale.removals()) {
                inventory.setItem(removal.slot(), null);
            }
        }
    }

    private void restorePlannedItems(final Inventory inventory, final SalePlanning planning) {
        for (final GroupedPlannedSale sale : planning.plannedSales()) {
            for (final InventoryRemoval removal : sale.removals()) {
                inventory.setItem(removal.slot(), removal.originalStack());
            }
        }
    }

    private SupportedContainerTarget resolveSupportedBlockTarget(final Block targetBlock) {
        if (targetBlock == null) {
            return null;
        }

        final BlockState state = targetBlock.getState();
        final String description = this.describeTargetBlock(targetBlock);
        if (state instanceof Chest chest) {
            return new SupportedContainerTarget(targetBlock, state, chest.getInventory(), description);
        }
        if (state instanceof Barrel barrel) {
            return new SupportedContainerTarget(targetBlock, state, barrel.getInventory(), description);
        }
        if (state instanceof ShulkerBox shulkerBox) {
            return new SupportedContainerTarget(targetBlock, state, shulkerBox.getInventory(), description);
        }
        return null;
    }

    private boolean isHeldShulkerItem(final ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !this.isShulkerBoxItem(stack.getType())) {
            return false;
        }
        return stack.getItemMeta() instanceof BlockStateMeta blockStateMeta
            && blockStateMeta.getBlockState() instanceof ShulkerBox;
    }

    private boolean isShulkerBoxItem(final Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }

    private boolean isLocked(final BlockState state) {
        return state instanceof Lockable lockable && lockable.isLocked();
    }

    private SellContainerResult deniedPlacedContainer(final String targetDescription, final String message) {
        return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), targetDescription, message);
    }

    private String describeTargetBlock(final Block targetBlock) {
        if (targetBlock == null) {
            return null;
        }
        return this.friendlyMaterialName(targetBlock.getType()).toLowerCase();
    }

    private String friendlyMaterialName(final Material material) {
        final String[] parts = material.name().toLowerCase().split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private record SalePlanning(
        List<GroupedPlannedSale> plannedSales,
        List<String> skippedDescriptions,
        BigDecimal totalEarned,
        boolean taperedAny
    ) {
    }

    private record GroupedPlannedSale(
        ItemKey itemKey,
        String displayName,
        int amount,
        SellQuote quote,
        List<InventoryRemoval> removals
    ) {
    }

    private record InventoryRemoval(int slot, ItemStack originalStack) {
    }

    private record SupportedContainerTarget(Block block, BlockState state, Inventory inventory, String description) {
    }

    private static final class GroupedSaleAccumulator {

        private final ItemKey itemKey;
        private final String displayName;
        private final List<InventoryRemoval> removals;
        private int totalAmount;

        private GroupedSaleAccumulator(final ItemKey itemKey, final String displayName) {
            this.itemKey = itemKey;
            this.displayName = displayName;
            this.removals = new ArrayList<>();
            this.totalAmount = 0;
        }

        private void add(final int slot, final ItemStack originalStack) {
            this.removals.add(new InventoryRemoval(slot, originalStack.clone()));
            this.totalAmount += originalStack.getAmount();
        }

        private ItemKey itemKey() {
            return this.itemKey;
        }

        private String displayName() {
            return this.displayName;
        }

        private int totalAmount() {
            return this.totalAmount;
        }

        private GroupedPlannedSale toPlannedSale(final SellQuote quote) {
            return new GroupedPlannedSale(
                this.itemKey,
                this.displayName,
                this.totalAmount,
                quote,
                List.copyOf(this.removals)
            );
        }
    }
}
```
