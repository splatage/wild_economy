package com.splatage.wild_economy.economy.repository;

import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

public interface EconomyNameCacheRepository {

    void upsert(Connection connection, UUID playerId, String lastName, long updatedAtEpochSecond);

    String findLastKnownName(UUID playerId);

    Map<UUID, String> findLastKnownNames(Iterable<UUID> playerIds);
}
