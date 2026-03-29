package com.splatage.wild_economy.economy.model;

import java.util.UUID;

public record EconomyLedgerEntry(
    UUID playerId,
    EconomyReason reason,
    MoneyAmount amount,
    MoneyAmount balanceAfter,
    UUID counterpartyId,
    String referenceType,
    String referenceId,
    long createdAtEpochSecond
) {}
