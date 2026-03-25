package com.splatage.wild_economy.economy.service;

import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.EconomyTransferResult;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import java.util.UUID;

public interface EconomyService {

    void warmPlayerSession(UUID playerId, String playerName);

    void flushPlayerSession(UUID playerId, String playerName);

    MoneyAmount getBalance(UUID playerId);

    MoneyAmount getBalanceForSensitiveOperation(UUID playerId);

    EconomyMutationResult deposit(
        UUID playerId,
        MoneyAmount amount,
        EconomyReason reason,
        String referenceType,
        String referenceId
    );

    EconomyMutationResult withdraw(
        UUID playerId,
        MoneyAmount amount,
        EconomyReason reason,
        String referenceType,
        String referenceId
    );

    EconomyMutationResult setBalance(
        UUID playerId,
        MoneyAmount balance,
        EconomyReason reason,
        String referenceType,
        String referenceId
    );

    EconomyTransferResult transfer(
        UUID senderId,
        UUID recipientId,
        MoneyAmount amount,
        String referenceType,
        String referenceId
    );

    void invalidate(UUID playerId);
}
