package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record BuyQuote(
    ItemKey itemKey,
    int amount,
    BigDecimal unitPrice,
    BigDecimal totalPrice
) {}
