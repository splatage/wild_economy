package com.splatage.wild_economy.economy.repository;

import com.splatage.wild_economy.economy.model.EconomyAccountRecord;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

public interface EconomyAccountRepository {

    EconomyAccountRecord findByPlayerId(UUID playerId);

    EconomyAccountRecord findByPlayerId(Connection connection, UUID playerId);

    EconomyAccountRecord createIfAbsent(Connection connection, UUID playerId, long updatedAtEpochSecond);

    void upsert(Connection connection, UUID playerId, MoneyAmount balance, long updatedAtEpochSecond);

    boolean updateIfBalanceMatches(
        Connection connection,
        UUID playerId,
        MoneyAmount expectedBalance,
        MoneyAmount newBalance,
        long updatedAtEpochSecond
    );

    List<EconomyAccountRecord> findTopAccounts(int limit, int offset);

    int findRank(UUID playerId);
}
