package com.splatage.wild_economy.exchange.domain;

public record StockSnapshot(
    ItemKey itemKey,
    long stockCount,
    long stockCap,
    double fillRatio,
    StockState stockState
) {}
