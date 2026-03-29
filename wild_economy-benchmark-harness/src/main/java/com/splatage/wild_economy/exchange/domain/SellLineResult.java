package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record SellLineResult(
    ItemKey itemKey,
    String displayName,
    int amountSold,
    BigDecimal effectiveUnitPrice,
    BigDecimal totalEarned,
    boolean tapered
) {}
