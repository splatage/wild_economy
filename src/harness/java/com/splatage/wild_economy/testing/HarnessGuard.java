package com.splatage.wild_economy.testing;

import com.splatage.wild_economy.config.DatabaseConfig;
import java.util.List;
import java.util.Objects;

public final class HarnessGuard {

    public void validate(final HarnessConfig harnessConfig, final DatabaseConfig databaseConfig, final boolean resetRequested) {
        Objects.requireNonNull(harnessConfig, "harnessConfig");
        Objects.requireNonNull(databaseConfig, "databaseConfig");

        if (!harnessConfig.enabled()) {
            throw new IllegalStateException("Harness execution is disabled in harness.yml");
        }
        if (resetRequested && !harnessConfig.allowReset()) {
            throw new IllegalStateException("Harness reset was requested, but allow-reset is false");
        }

        final String marker = harnessConfig.requiredPrefixMarker();
        final List<String> prefixes = List.of(
                databaseConfig.economyTablePrefix(),
                databaseConfig.exchangeTablePrefix(),
                databaseConfig.storeTablePrefix()
        );
        for (final String prefix : prefixes) {
            if (prefix == null || prefix.isBlank()) {
                throw new IllegalStateException("Harness target prefix cannot be blank");
            }
            if (marker != null && !marker.isBlank() && !prefix.contains(marker)) {
                throw new IllegalStateException(
                        "Harness refused to run because prefix '" + prefix + "' does not include required marker '" + marker + "'"
                );
            }
        }
    }
}
