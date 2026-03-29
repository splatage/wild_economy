package com.splatage.wild_economy.economy.repository.sqlite;

import com.splatage.wild_economy.economy.repository.EconomyNameCacheRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SqliteEconomyNameCacheRepository implements EconomyNameCacheRepository {

    private final DatabaseProvider databaseProvider;
    private final String nameCacheTableName;

    public SqliteEconomyNameCacheRepository(final DatabaseProvider databaseProvider, final String economyTablePrefix) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
        this.nameCacheTableName = Objects.requireNonNull(economyTablePrefix, "economyTablePrefix") + "economy_name_cache";
    }

    @Override
    public void upsert(
        final Connection connection,
        final UUID playerId,
        final String lastName,
        final long updatedAtEpochSecond
    ) {
        final String sql = """
            INSERT INTO %s (player_uuid, last_name, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                last_name = excluded.last_name,
                updated_at = excluded.updated_at
            """.formatted(this.nameCacheTableName);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, lastName);
            statement.setLong(3, updatedAtEpochSecond);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to upsert economy name cache for " + playerId, exception);
        }
    }

    @Override
    public String findLastKnownName(final UUID playerId) {
        final String sql = "SELECT last_name FROM " + this.nameCacheTableName + " WHERE player_uuid = ?";
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return resultSet.getString("last_name");
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load cached player name for " + playerId, exception);
        }
    }

    @Override
    public Map<UUID, String> findLastKnownNames(final Iterable<UUID> playerIds) {
        final List<UUID> ids = new ArrayList<>();
        for (final UUID playerId : playerIds) {
            ids.add(playerId);
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        final String placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        final String sql = "SELECT player_uuid, last_name FROM " + this.nameCacheTableName
                + " WHERE player_uuid IN (" + placeholders + ")";

        final Map<UUID, String> result = new LinkedHashMap<>();
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (final UUID playerId : ids) {
                statement.setString(index++, playerId.toString());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.put(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("last_name")
                    );
                }
            }
            return Map.copyOf(result);
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load cached player names", exception);
        }
    }
}
