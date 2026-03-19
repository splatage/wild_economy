package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SqliteExchangeTransactionRepository implements ExchangeTransactionRepository {

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
        throw new UnsupportedOperationException("Not implemented");
    }
}
