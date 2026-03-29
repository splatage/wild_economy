package com.splatage.wild_economy.testing.verify;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.exchange.activity.MarketActivityRecord;
import com.splatage.wild_economy.exchange.supplier.SupplierAggregateRow;
import com.splatage.wild_economy.testing.seed.SeedPlan;
import com.splatage.wild_economy.testing.support.HarnessSqlSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DatasetVerifierImpl implements DatasetVerifier {

    @Override
    public InvariantReport verify(final HarnessBootstrap.HarnessComponents components, final SeedPlan seedPlan) {
        final List<InvariantViolation> violations = new ArrayList<>();
        this.verifyNegativeBalances(components, violations);
        this.verifyNegativeStock(components, violations);
        this.verifyItemKeysResolve(components, violations);
        this.verifyRecentWindowQueries(components, seedPlan, violations);
        this.verifySupplierTotals(components, seedPlan, violations);
        return new InvariantReport(violations.isEmpty(), List.copyOf(violations));
    }

    private void verifyNegativeBalances(
            final HarnessBootstrap.HarnessComponents components,
            final List<InvariantViolation> violations
    ) {
        components.transactionRunner().run(connection -> {
            final String table = components.databaseConfig().economyTablePrefix() + "economy_accounts";
            final long negativeRows = HarnessSqlSupport.countWhereLessThanZero(connection, table, "balance_minor");
            if (negativeRows > 0L) {
                violations.add(new InvariantViolation("negative_balances", "Found " + negativeRows + " economy account rows with negative balances."));
            }
            return null;
        });
    }

    private void verifyNegativeStock(
            final HarnessBootstrap.HarnessComponents components,
            final List<InvariantViolation> violations
    ) {
        components.transactionRunner().run(connection -> {
            final String table = components.databaseConfig().exchangeTablePrefix() + "exchange_stock";
            final long negativeRows = HarnessSqlSupport.countWhereLessThanZero(connection, table, "stock_count");
            if (negativeRows > 0L) {
                violations.add(new InvariantViolation("negative_stock", "Found " + negativeRows + " exchange stock rows with negative stock."));
            }
            return null;
        });
    }

    private void verifyItemKeysResolve(
            final HarnessBootstrap.HarnessComponents components,
            final List<InvariantViolation> violations
    ) {
        components.transactionRunner().run(connection -> {
            final Set<String> stockKeys = HarnessSqlSupport.distinctStrings(
                    connection,
                    components.databaseConfig().exchangeTablePrefix() + "exchange_stock",
                    "item_key"
            );
            final Set<String> transactionKeys = HarnessSqlSupport.distinctStrings(
                    connection,
                    components.databaseConfig().exchangeTablePrefix() + "exchange_transactions",
                    "item_key"
            );
            final Set<String> supplierKeys = HarnessSqlSupport.distinctStrings(
                    connection,
                    components.databaseConfig().exchangeTablePrefix() + "supplier_all_time",
                    "item_key"
            );
            for (final String itemKey : stockKeys) {
                if (components.exchangeCatalog().get(new com.splatage.wild_economy.exchange.domain.ItemKey(itemKey)).isEmpty()) {
                    violations.add(new InvariantViolation("unknown_stock_item_key", "Stock row references unknown item key '" + itemKey + "'."));
                }
            }
            for (final String itemKey : transactionKeys) {
                if (components.exchangeCatalog().get(new com.splatage.wild_economy.exchange.domain.ItemKey(itemKey)).isEmpty()) {
                    violations.add(new InvariantViolation("unknown_transaction_item_key", "Transaction row references unknown item key '" + itemKey + "'."));
                }
            }
            for (final String itemKey : supplierKeys) {
                if (components.exchangeCatalog().get(new com.splatage.wild_economy.exchange.domain.ItemKey(itemKey)).isEmpty()) {
                    violations.add(new InvariantViolation("unknown_supplier_item_key", "Supplier aggregate row references unknown item key '" + itemKey + "'."));
                }
            }
            return null;
        });
    }

    private void verifyRecentWindowQueries(
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan,
            final List<InvariantViolation> violations
    ) {
        final long sinceEpochSecond = seedPlan.nowEpochSecond() - (components.globalConfig().recentWindowHours() * 3_600L);
        for (final MarketActivityRecord record : components.exchangeTransactionRepository().loadRecentlyPurchased(sinceEpochSecond, 512)) {
            if (record.eventEpochSecond() < sinceEpochSecond) {
                violations.add(new InvariantViolation(
                        "purchase_outside_recent_window",
                        "Recent purchase activity returned an event older than the configured recent window: " + record.itemKey()
                ));
                break;
            }
        }
        for (final MarketActivityRecord record : components.supplierStatsRepository().loadRecentlyStocked(sinceEpochSecond, 512)) {
            if (record.eventEpochSecond() < sinceEpochSecond) {
                violations.add(new InvariantViolation(
                        "stocked_outside_recent_window",
                        "Recently stocked activity returned an event older than the configured recent window: " + record.itemKey()
                ));
                break;
            }
        }
    }

    private void verifySupplierTotals(
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan,
            final List<InvariantViolation> violations
    ) {
        final String currentWeekKey = this.weekKey(seedPlan.nowEpochSecond());
        final Map<String, Long> weeklyByPlayerAndItem = new HashMap<>();
        for (final SupplierAggregateRow row : components.supplierStatsRepository().loadWeeklyRows(currentWeekKey)) {
            weeklyByPlayerAndItem.put(row.playerId() + "|" + row.itemKey(), row.quantitySold());
        }
        final Map<String, Long> allTimeByPlayerAndItem = new HashMap<>();
        for (final SupplierAggregateRow row : components.supplierStatsRepository().loadAllTimeRows()) {
            allTimeByPlayerAndItem.put(row.playerId() + "|" + row.itemKey(), row.quantitySold());
        }
        for (final Map.Entry<String, Long> entry : weeklyByPlayerAndItem.entrySet()) {
            final long allTimeValue = allTimeByPlayerAndItem.getOrDefault(entry.getKey(), 0L);
            if (allTimeValue < entry.getValue()) {
                violations.add(new InvariantViolation(
                        "weekly_exceeds_all_time",
                        "Supplier aggregate key '" + entry.getKey() + "' has weekly=" + entry.getValue() + " but all-time=" + allTimeValue
                ));
            }
        }
    }

    private String weekKey(final long epochSecond) {
        final java.time.LocalDate date = Instant.ofEpochSecond(epochSecond).atZone(ZoneOffset.UTC).toLocalDate();
        final WeekFields weekFields = WeekFields.ISO;
        final int year = date.get(weekFields.weekBasedYear());
        final int week = date.get(weekFields.weekOfWeekBasedYear());
        return "%04d-W%02d".formatted(year, week);
    }
}
