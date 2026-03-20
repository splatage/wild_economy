package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;
import java.util.List;

public record SellContainerResult(
    boolean success,
    List<SellLineResult> soldLines,
    BigDecimal totalEarned,
    List<String> skippedDescriptions,
    String targetDescription,
    String message
) {
}
