package com.splatage.wild_economy.exchange.activity;

import java.math.BigDecimal;

public record MarketActivityRecord(
    String itemKey,
    long eventEpochSecond,
    BigDecimal totalValue,
    int amount
) {}
