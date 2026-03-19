package com.splatage.wild_economy.persistence;

import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigrationManager {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V(\\d+)__.*\\.sql");

    private final DatabaseProvider databaseProvider;
    private final SchemaVersionRepository schemaVersionRepository;

    public MigrationManager(
        final DatabaseProvider databaseProvider,
        final SchemaVersionRepository schemaVersionRepository
    ) {
        this.databaseProvider = Objects.requireNonNull(databaseProvider, "databaseProvider");
        this.schemaVersionRepository = Objects.requireNonNull(schemaVersionRepository, "schemaVersionRepository");
    }

    public void migrate() {
        final int currentVersion = this.safeGetCurrentVersion();
        final List<MigrationResource> migrations = this.loadKnownMigrations();
        migrations.sort(Comparator.comparingInt(MigrationResource::version));

        for (final MigrationResource migration : migrations) {
            if (migration.version() <= currentVersion) {
                continue;
            }
            this.applyMigration(migration);
            this.schemaVersionRepository.setCurrentVersion(migration.version());
        }
    }

    private int safeGetCurrentVersion() {
        try {
            return this.schemaVersionRepository.getCurrentVersion();
        } catch (final RuntimeException ignored) {
            return 0;
        }
    }

    private List<MigrationResource> loadKnownMigrations() {
        final String basePath = switch (this.databaseProvider.dialect()) {
            case SQLITE -> "/db/migration/sqlite/";
            case MYSQL -> "/db/migration/mysql/";
        };

        final List<MigrationResource> migrations = new ArrayList<>();
        // v1 only for now
        final String resourceName = "V1__initial_schema.sql";
        final int version = this.extractVersion(resourceName);
        final String sql = this.readResource(basePath + resourceName);
        migrations.add(new MigrationResource(version, resourceName, sql));
        return migrations;
    }

    private void applyMigration(final MigrationResource migration) {
        try (Connection connection = this.databaseProvider.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            for (final String sqlPart : this.splitStatements(migration.sql())) {
                final String trimmed = sqlPart.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
            connection.commit();
        } catch (final SQLException exception) {
            throw new IllegalStateException("Failed to apply migration " + migration.name(), exception);
        }
    }

    private List<String> splitStatements(final String sql) {
        final List<String> parts = new ArrayList<>();
        final String[] split = sql.split(";\\s*(?:\\r?\\n|$)");
        for (final String part : split) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private int extractVersion(final String resourceName) {
        final Matcher matcher = VERSION_PATTERN.matcher(resourceName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Invalid migration name: " + resourceName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String readResource(final String resourcePath) {
        try (InputStream inputStream = this.getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing migration resource: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                final StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to read migration resource: " + resourcePath, exception);
        }
    }

    private record MigrationResource(int version, String name, String sql) {
    }
}
