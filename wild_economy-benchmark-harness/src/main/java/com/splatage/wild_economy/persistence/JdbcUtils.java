package com.splatage.wild_economy.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcUtils {

    private JdbcUtils() {
    }

    public static void closeQuietly(final AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (final Exception ignored) {
        }
    }

    public static boolean hasColumn(final ResultSet resultSet, final String columnName) throws SQLException {
        final var meta = resultSet.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (columnName.equalsIgnoreCase(meta.getColumnName(i))) {
                return true;
            }
        }
        return false;
    }

    public static void bindNullableString(final PreparedStatement statement, final int index, final String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
