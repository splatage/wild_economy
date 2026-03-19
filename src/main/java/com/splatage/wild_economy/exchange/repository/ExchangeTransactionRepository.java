package com.splatage.wild_economy.exchange.repository;

import com.splatage.wild_economy.exchange.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
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
}
