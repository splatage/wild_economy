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
        Objects.requireNonNull(work, "work");

        try (Connection connection = this.databaseProvider.getConnection()) {
            final boolean originalAutoCommit = connection.getAutoCommit();
            if (originalAutoCommit) {
                connection.setAutoCommit(false);
            }

            try {
                final T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (final Throwable throwable) {
                this.rollbackQuietly(connection, throwable);
                throw this.rethrow(throwable);
            } finally {
                this.restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (final SQLException exception) {
            throw new IllegalStateException("Transactional database operation failed", exception);
        }
    }

    private void rollbackQuietly(final Connection connection, final Throwable originalFailure) {
        try {
            connection.rollback();
        } catch (final SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit(final Connection connection, final boolean originalAutoCommit) throws SQLException {
        if (connection.isClosed()) {
            return;
        }
        if (connection.getAutoCommit() != originalAutoCommit) {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private RuntimeException rethrow(final Throwable throwable) throws SQLException {
        if (throwable instanceof final SQLException sqlException) {
            throw sqlException;
        }
        if (throwable instanceof final RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof final Error error) {
            throw error;
        }
        return new IllegalStateException("Transactional work failed", throwable);
    }

    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
