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
