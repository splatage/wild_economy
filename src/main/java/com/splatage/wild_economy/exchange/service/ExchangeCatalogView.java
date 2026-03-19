package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockState;
import java.math.BigDecimal;

public record ExchangeCatalogView(
    ItemKey itemKey,
    String displayName,
    BigDecimal buyPrice,
    long stockCount,
    StockState stockState
) {}
