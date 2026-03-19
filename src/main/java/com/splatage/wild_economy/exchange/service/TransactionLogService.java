package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;

public interface TransactionLogService {
    void logSale(UUID playerId, ItemKey itemKey, int amount, BigDecimal unitPrice, BigDecimal totalValue);
    void logPurchase(UUID playerId, ItemKey itemKey, int amount, BigDecimal unitPrice, BigDecimal totalValue);
    void logTurnover(ItemKey itemKey, int amount);
}
