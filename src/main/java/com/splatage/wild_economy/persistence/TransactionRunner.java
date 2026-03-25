package com.splatage.wild_economy.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class TransactionRunner {

    private final DatabaseProvider databaseProvider;

    public TransactionRunner(final DatabaseProvider databaseProvider) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
    }

    public <T> T run(final TransactionWork<T> work) {
        try (Connection connection = this.databaseProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                final T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Transactional database operation failed", exception);
        }
    }

    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
