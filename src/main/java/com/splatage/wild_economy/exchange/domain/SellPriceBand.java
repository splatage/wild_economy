package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record SellPriceBand(
    long minStockInclusive,
    long maxStockInclusive,
    BigDecimal minUnitPrice
) {
}
