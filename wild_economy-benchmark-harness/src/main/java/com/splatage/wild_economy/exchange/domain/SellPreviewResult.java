package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;
import java.util.List;

public record SellPreviewResult(
    boolean success,
    List<SellPreviewLine> lines,
    BigDecimal totalQuoted,
    List<String> skippedDescriptions,
    String message
) {}
