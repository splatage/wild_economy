package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record SellPriceBand(
    double minFillRatioInclusive,
    double maxFillRatioExclusive,
    BigDecimal multiplier
) {}
package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record SellPriceBand(
    double minFillRatioInclusive,
    double maxFillRatioExclusive,
    BigDecimal multiplier
) {}
