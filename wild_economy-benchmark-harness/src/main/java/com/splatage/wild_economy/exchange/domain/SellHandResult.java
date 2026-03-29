package com.splatage.wild_economy.exchange.domain;

public record SellHandResult(
    boolean success,
    SellLineResult lineResult,
    RejectionReason rejectionReason,
    String message
) {}
