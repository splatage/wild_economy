package com.splatage.wild_economy.exchange.service;

public final class TransactionLogServiceImpl implements TransactionLogService {
}
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TransactionLogServiceImpl implements TransactionLogService {

    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    private final ExchangeTransactionRepository transactionRepository;

    public TransactionLogServiceImpl(final ExchangeTransactionRepository transactionRepository) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
    }

    @Override
    public void logSale(
        final UUID playerId,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal unitPrice,
        final BigDecimal totalValue
    ) {
        this.transactionRepository.insert(
            TransactionType.SELL,
            playerId,
            itemKey.value(),
            amount,
            unitPrice,
            totalValue,
            Instant.now(),
            null
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
        this.transactionRepository.insert(
            TransactionType.BUY,
            playerId,
            itemKey.value(),
            amount,
            unitPrice,
            totalValue,
            Instant.now(),
            null
        );
    }

    @Override
    public void logTurnover(final ItemKey itemKey, final int amount) {
        this.transactionRepository.insert(
            TransactionType.TURNOVER,
            SYSTEM_UUID,
            itemKey.value(),
            amount,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Instant.now(),
            null
        );
    }
}
