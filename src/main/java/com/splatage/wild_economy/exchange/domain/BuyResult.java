package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record BuyResult(
    boolean success,
    ItemKey itemKey,
    int amountBought,
    BigDecimal unitPrice,
    BigDecimal totalCost,
    RejectionReason rejectionReason,
    String message
) {}
