package com.splatage.wild_economy.exchange.repository;

import com.splatage.wild_economy.exchange.activity.MarketActivityRecord;
import com.splatage.wild_economy.exchange.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExchangeTransactionRepository {
    void insert(
        TransactionType type,
        UUID playerId,
        String itemKey,
        int amount,
        BigDecimal unitPrice,
        BigDecimal totalValue,
        Instant createdAt,
        String metaJson
    );

    List<MarketActivityRecord> loadRecentlyPurchased(long sinceEpochSecond, int limit);

    List<MarketActivityRecord> loadTopTurnover(long sinceEpochSecond, int limit);

    List<MarketActivityRecord> loadPlayerRecentPurchases(UUID playerId, long sinceEpochSecond, int limit);
}
