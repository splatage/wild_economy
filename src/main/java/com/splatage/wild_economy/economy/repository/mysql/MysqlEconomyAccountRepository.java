package com.splatage.wild_economy.economy.repository.mysql;

import com.splatage.wild_economy.economy.model.EconomyAccountRecord;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.economy.repository.EconomyAccountRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MysqlEconomyAccountRepository implements EconomyAccountRepository {

    private final DatabaseProvider databaseProvider;
    private final String accountsTableName;

    public MysqlEconomyAccountRepository(final DatabaseProvider databaseProvider, final String economyTablePrefix) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
        this.accountsTableName = Objects.requireNonNull(economyTablePrefix, "economyTablePrefix") + "economy_accounts";
    }

    @Override
    public EconomyAccountRecord findByPlayerId(final UUID playerId) {
        try (Connection connection = this.databaseProvider.getConnection()) {
            return this.findByPlayerId(connection, playerId);
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load economy account for " + playerId, exception);
        }
    }

    @Override
    public EconomyAccountRecord findByPlayerId(final Connection connection, final UUID playerId) {
        final String sql = "SELECT player_uuid, balance_minor, updated_at FROM " + this.accountsTableName + " WHERE player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new EconomyAccountRecord(
                        UUID.fromString(resultSet.getString("player_uuid")),
                        MoneyAmount.ofMinor(resultSet.getLong("balance_minor")),
                        resultSet.getLong("updated_at")
                );
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load economy account for " + playerId, exception);
        }
    }

    @Override
    public EconomyAccountRecord createIfAbsent(
        final Connection connection,
        final UUID playerId,
        final long updatedAtEpochSecond
    ) {
        final String insertSql = "INSERT IGNORE INTO " + this.accountsTableName
                + " (player_uuid, balance_minor, updated_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, 0L);
            statement.setLong(3, updatedAtEpochSecond);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to create economy account for " + playerId, exception);
        }
        return this.findByPlayerId(connection, playerId);
    }

    @Override
    public void upsert(
        final Connection connection,
        final UUID playerId,
        final MoneyAmount balance,
        final long updatedAtEpochSecond
    ) {
        final String sql = """
            INSERT INTO %s (player_uuid, balance_minor, updated_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                balance_minor = VALUES(balance_minor),
                updated_at = VALUES(updated_at)
            """.formatted(this.accountsTableName);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, balance.minorUnits());
            statement.setLong(3, updatedAtEpochSecond);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to upsert economy account for " + playerId, exception);
        }
    }

    @Override
    public boolean updateIfBalanceMatches(
        final Connection connection,
        final UUID playerId,
        final MoneyAmount expectedBalance,
        final MoneyAmount newBalance,
        final long updatedAtEpochSecond
    ) {
        final String sql = "UPDATE " + this.accountsTableName
                + " SET balance_minor = ?, updated_at = ? WHERE player_uuid = ? AND balance_minor = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, newBalance.minorUnits());
            statement.setLong(2, updatedAtEpochSecond);
            statement.setString(3, playerId.toString());
            statement.setLong(4, expectedBalance.minorUnits());
            return statement.executeUpdate() == 1;
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to compare-and-swap economy account for " + playerId, exception);
        }
    }

    @Override
    public List<EconomyAccountRecord> findTopAccounts(final int limit, final int offset) {
        final String sql = "SELECT player_uuid, balance_minor, updated_at FROM " + this.accountsTableName
                + " ORDER BY balance_minor DESC, player_uuid ASC LIMIT ? OFFSET ?";

        final List<EconomyAccountRecord> accounts = new ArrayList<>();
        try (Connection connection = this.databaseProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    accounts.add(new EconomyAccountRecord(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            MoneyAmount.ofMinor(resultSet.getLong("balance_minor")),
                            resultSet.getLong("updated_at")
                    ));
                }
            }
            return List.copyOf(accounts);
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to load baltop accounts", exception);
        }
    }
}
