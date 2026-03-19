package com.splatage.wild_economy.economy;

import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyGateway {
    EconomyResult deposit(UUID playerId, BigDecimal amount);
    EconomyResult withdraw(UUID playerId, BigDecimal amount);
    BigDecimal getBalance(UUID playerId);
}
package com.splatage.wild_economy.economy;

import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyGateway {
    EconomyResult deposit(UUID playerId, BigDecimal amount);
    EconomyResult withdraw(UUID playerId, BigDecimal amount);
    BigDecimal getBalance(UUID playerId);
}
