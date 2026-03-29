package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record SellQuote(
    ItemKey itemKey,
    int amount,
    BigDecimal baseUnitPrice,
    BigDecimal effectiveUnitPrice,
    BigDecimal totalPrice,
    double stockFillRatio,
    boolean tapered
) {}
