package com.splatage.wild_economy.economy.repository.sqlite;

import com.splatage.wild_economy.economy.model.EconomyLedgerEntry;
import com.splatage.wild_economy.economy.repository.EconomyLedgerRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.JdbcUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public final class SqliteEconomyLedgerRepository implements EconomyLedgerRepository {

    private final String ledgerTableName;

    public SqliteEconomyLedgerRepository(final DatabaseProvider databaseProvider, final String economyTablePrefix) {
        Objects.requireNonNull(databaseProvider, "databaseProvider");
        this.ledgerTableName = Objects.requireNonNull(economyTablePrefix, "economyTablePrefix") + "economy_ledger";
    }

    @Override
    public void insert(final Connection connection, final EconomyLedgerEntry entry) {
        final String sql = "INSERT INTO " + this.ledgerTableName
                + " (player_uuid, entry_type, amount_minor, balance_after_minor, counterparty_uuid, reference_type, reference_id, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.playerId().toString());
            statement.setString(2, entry.reason().name());
            statement.setLong(3, entry.amount().minorUnits());
            statement.setLong(4, entry.balanceAfter().minorUnits());
            JdbcUtils.bindNullableString(statement, 5, entry.counterpartyId() == null ? null : entry.counterpartyId().toString());
            JdbcUtils.bindNullableString(statement, 6, entry.referenceType());
            JdbcUtils.bindNullableString(statement, 7, entry.referenceId());
            statement.setLong(8, entry.createdAtEpochSecond());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to insert economy ledger entry", exception);
        }
    }
}
