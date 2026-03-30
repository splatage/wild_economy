package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.item.ExchangeItemCodec;
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
    private final ExchangeItemCodec exchangeItemCodec;

    public TransactionLogServiceImpl(
        final ExchangeTransactionRepository transactionRepository,
        final Logger logger,
        final DatabaseDialect dialect,
        final int mysqlMaximumPoolSize
    ) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = this.createExecutor(dialect, mysqlMaximumPoolSize);
        this.exchangeItemCodec = new ExchangeItemCodec();
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
            this.executor.submit(() -> {
                try {
                    this.transactionRepository.insert(
                        type,
                        playerId,
                        itemKey.value(),
                        amount,
                        unitPrice,
                        totalValue,
                        Instant.now(),
                        this.exchangeItemCodec.metadataJson(itemKey)
                    );
                } catch (final RuntimeException exception) {
                    this.logger.log(
                        Level.WARNING,
                        "Failed to persist transaction log entry for " + type + " on " + itemKey.value() + ".",
                        exception
                    );
                }
            });
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
