package com.splatage.wild_economy.testing.support;

import com.splatage.wild_economy.economy.model.MoneyAmount;
import java.util.UUID;

public record SeedPlayer(
        UUID playerId,
        String playerName,
        MoneyAmount startingBalance
) {
}
