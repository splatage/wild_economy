package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record SellPreviewLine(
    ItemKey itemKey,
    String displayName,
    int amountQuoted,
    BigDecimal effectiveUnitPrice,
    BigDecimal totalQuoted,
    StockState stockState,
    boolean tapered
) {}
