package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;
import java.util.List;

public record SellAllResult(
    boolean success,
    List<SellLineResult> soldLines,
    BigDecimal totalEarned,
    List<String> skippedDescriptions,
    String message
) {}
