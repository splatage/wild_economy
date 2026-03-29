package com.splatage.wild_economy.economy.model;

import java.util.UUID;

public record BalanceRankEntry(
    int rank,
    UUID playerId,
    String displayName,
    MoneyAmount balance
) {}
