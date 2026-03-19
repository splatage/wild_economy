package com.splatage.wild_economy.economy;

import java.math.BigDecimal;
import java.util.UUID;

public final class VaultEconomyGateway implements EconomyGateway {

    @Override
    public EconomyResult deposit(final UUID playerId, final BigDecimal amount) {
        return new EconomyResult(false, "Not implemented");
    }

    @Override
    public EconomyResult withdraw(final UUID playerId, final BigDecimal amount) {
        return new EconomyResult(false, "Not implemented");
    }

    @Override
    public BigDecimal getBalance(final UUID playerId) {
        return BigDecimal.ZERO;
    }
}
