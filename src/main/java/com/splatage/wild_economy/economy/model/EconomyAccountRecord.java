package com.splatage.wild_economy.economy.model;

import java.util.UUID;

public record EconomyAccountRecord(
    UUID playerId,
    MoneyAmount balance,
    long updatedAtEpochSecond
) {}
