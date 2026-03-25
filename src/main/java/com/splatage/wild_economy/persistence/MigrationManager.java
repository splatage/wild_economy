package com.splatage.wild_economy.persistence;

import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
        final List<MigrationResource> migrations = this.loadMigrations();
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

    private List<MigrationResource> loadMigrations() {
        final String resourceDirectory = switch (this.databaseProvider.dialect()) {
            case SQLITE -> "db/migration/sqlite/";
            case MYSQL -> "db/migration/mysql/";
        };

        final List<String> resourceNames = this.discoverMigrationResourceNames(resourceDirectory);
        final List<MigrationResource> migrations = new ArrayList<>(resourceNames.size());
        final Set<Integer> seenVersions = new HashSet<>();

        for (final String resourceName : resourceNames) {
            final int version = this.extractVersion(resourceName);
            if (!seenVersions.add(version)) {
                throw new IllegalStateException(
                    "Duplicate migration version " + version + " under /" + resourceDirectory
                );
            }
            final String sql = this.readResource('/' + resourceDirectory + resourceName);
            migrations.add(new MigrationResource(version, resourceName, sql));
        }

        return migrations;
    }

    private List<String> discoverMigrationResourceNames(final String resourceDirectory) {
        final URL directoryUrl = this.getClass().getClassLoader().getResource(resourceDirectory);
        final List<String> discovered;

        if (directoryUrl != null) {
            discovered = switch (directoryUrl.getProtocol()) {
                case "file" -> this.discoverFromDirectoryUrl(directoryUrl);
                case "jar" -> this.discoverFromJarUrl(directoryUrl, resourceDirectory);
                default -> throw new IllegalStateException(
                    "Unsupported migration resource protocol: " + directoryUrl.getProtocol()
                );
            };
        } else {
            discovered = this.discoverFromCodeSource(resourceDirectory);
        }

        if (discovered.isEmpty()) {
            throw new IllegalStateException("No migration resources found under /" + resourceDirectory);
        }

        discovered.sort(Comparator.comparingInt(this::extractVersion));
        return discovered;
    }

    private List<String> discoverFromCodeSource(final String resourceDirectory) {
        final URL codeSourceUrl = this.getClass().getProtectionDomain().getCodeSource().getLocation();
        if (codeSourceUrl == null) {
            throw new IllegalStateException(
                "Unable to resolve code source for migration discovery under /" + resourceDirectory
            );
        }

        final Path codeSourcePath;
        try {
            codeSourcePath = Paths.get(codeSourceUrl.toURI());
        } catch (final URISyntaxException exception) {
            throw new IllegalStateException("Invalid code source URL: " + codeSourceUrl, exception);
        }

        if (Files.isDirectory(codeSourcePath)) {
            return this.discoverFromDirectoryPath(codeSourcePath.resolve(resourceDirectory));
        }
        if (Files.isRegularFile(codeSourcePath) && codeSourcePath.toString().endsWith(".jar")) {
            return this.discoverFromJarPath(codeSourcePath, resourceDirectory);
        }

        throw new IllegalStateException(
            "Unsupported code source for migration discovery: " + codeSourcePath
        );
    }

    private List<String> discoverFromDirectoryUrl(final URL directoryUrl) {
        final Path directoryPath;
        try {
            directoryPath = Paths.get(directoryUrl.toURI());
        } catch (final URISyntaxException exception) {
            throw new IllegalStateException("Invalid migration directory URL: " + directoryUrl, exception);
        }
        return this.discoverFromDirectoryPath(directoryPath);
    }

    private List<String> discoverFromDirectoryPath(final Path directoryPath) {
        if (!Files.isDirectory(directoryPath)) {
            throw new IllegalStateException("Migration resource path is not a directory: " + directoryPath);
        }

        try (Stream<Path> stream = Files.list(directoryPath)) {
            return stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(this::isMigrationFileName)
                .toList();
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to list migration directory: " + directoryPath, exception);
        }
    }

    private List<String> discoverFromJarUrl(final URL directoryUrl, final String resourceDirectory) {
        final JarURLConnection connection;
        try {
            connection = (JarURLConnection) directoryUrl.openConnection();
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to open jar resource: " + directoryUrl, exception);
        }

        try {
            return this.discoverFromJarFile(connection.getJarFile(), resourceDirectory);
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to inspect jar migrations under /" + resourceDirectory, exception);
        }
    }

    private List<String> discoverFromJarPath(final Path jarPath, final String resourceDirectory) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            return this.discoverFromJarFile(jarFile, resourceDirectory);
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to inspect jar migrations under /" + resourceDirectory, exception);
        }
    }

    private List<String> discoverFromJarFile(final JarFile jarFile, final String resourceDirectory) {
        final List<String> resourceNames = new ArrayList<>();
        final String prefix = resourceDirectory.endsWith("/") ? resourceDirectory : resourceDirectory + '/';

        for (final JarEntry entry : java.util.Collections.list(jarFile.entries())) {
            if (entry.isDirectory()) {
                continue;
            }
            final String entryName = entry.getName();
            if (!entryName.startsWith(prefix)) {
                continue;
            }
            final String relativeName = entryName.substring(prefix.length());
            if (relativeName.isEmpty() || relativeName.contains("/")) {
                continue;
            }
            if (this.isMigrationFileName(relativeName)) {
                resourceNames.add(relativeName);
            }
        }

        return resourceNames;
    }

    private boolean isMigrationFileName(final String resourceName) {
        return VERSION_PATTERN.matcher(resourceName).matches();
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
