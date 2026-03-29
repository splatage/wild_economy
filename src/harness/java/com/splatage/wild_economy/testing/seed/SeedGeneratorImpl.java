package com.splatage.wild_economy.testing.seed;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.economy.model.EconomyLedgerEntry;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StorePurchaseStatus;
import com.splatage.wild_economy.testing.TestProfile;
import com.splatage.wild_economy.testing.support.HarnessSqlSupport;
import com.splatage.wild_economy.testing.support.SeedPlayer;
import com.splatage.wild_economy.testing.support.SeedPlayerFactory;
import com.splatage.wild_economy.store.state.DirtyEntitlementGrant;
import com.splatage.wild_economy.store.state.StorePurchaseAuditRecord;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

public final class SeedGeneratorImpl implements SeedGenerator {

    private final Logger logger;
    private final SeedPlayerFactory seedPlayerFactory;

    public SeedGeneratorImpl(final Logger logger) {
        this.logger = logger;
        this.seedPlayerFactory = new SeedPlayerFactory();
    }

    @Override
    public SeedRunReport generate(final HarnessBootstrap.HarnessComponents components, final SeedPlan seedPlan) {
        final long startedAt = System.currentTimeMillis();
        final Random random = new Random(seedPlan.randomSeed());
        if (seedPlan.resetFirst()) {
            HarnessSqlSupport.resetSeededTables(components);
            this.logger.info("Harness reset completed for configured prefixes.");
        }

        final List<SeedPlayer> players = this.seedPlayerFactory.createPlayers(seedPlan, components.economyConfig().fractionalDigits());
        final List<ExchangeCatalogEntry> catalogEntries = this.sortedCatalogEntries(components);
        final int ledgerEntries = this.seedEconomy(players, components, seedPlan, random);
        final StockSeedSummary stockSummary = this.seedStock(catalogEntries, components, seedPlan, random);
        final int exchangeTransactions = this.seedExchangeTransactions(players, catalogEntries, components, seedPlan, random);
        final int supplierContributions = this.seedSupplierContributions(players, catalogEntries, components, seedPlan, random);
        final SeedStoreResult storeResult = this.seedStore(players, components, seedPlan, random);

        components.stockService().flushDirtyNow();
        components.storeRuntimeStateService().flushDirtyNow();

        final long durationMillis = System.currentTimeMillis() - startedAt;
        return new SeedRunReport(
                players.size(),
                ledgerEntries,
                catalogEntries.size(),
                this.countBuyEnabled(catalogEntries),
                this.countSellEnabled(catalogEntries),
                this.countPositiveSellQuoteEntries(catalogEntries, components),
                stockSummary.totalRowsSeeded(),
                stockSummary.rowsWithPositiveStock(),
                exchangeTransactions,
                supplierContributions,
                components.storeProductsConfig().products().size(),
                storeResult.entitlementsSeeded(),
                storeResult.purchasesSeeded(),
                durationMillis
        );
    }

    private int seedEconomy(
            final List<SeedPlayer> players,
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan,
            final Random random
    ) {
        components.transactionRunner().run(connection -> {
            for (final SeedPlayer player : players) {
                final long updatedAt = seedPlan.nowEpochSecond() - random.nextInt(86_400 * 21);
                components.economyAccountRepository().upsert(connection, player.playerId(), player.startingBalance(), updatedAt);
                components.economyNameCacheRepository().upsert(connection, player.playerId(), player.playerName(), updatedAt);
                components.economyLedgerRepository().insert(connection, new EconomyLedgerEntry(
                        player.playerId(),
                        EconomyReason.MIGRATION_ADJUSTMENT,
                        player.startingBalance(),
                        player.startingBalance(),
                        null,
                        "HARNESS_SEED",
                        seedPlan.profile().name().toLowerCase(Locale.ROOT),
                        updatedAt
                ));
            }
            return null;
        });
        return players.size();
    }

    private StockSeedSummary seedStock(
            final List<ExchangeCatalogEntry> entries,
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan,
            final Random random
    ) {
        if (entries.isEmpty()) {
            return new StockSeedSummary(0, 0);
        }

        final Map<ItemKey, Long> targetStocks = new LinkedHashMap<>();
        int positiveStockRows = 0;
        for (int index = 0; index < entries.size(); index++) {
            final ExchangeCatalogEntry entry = entries.get(index);
            final long targetStock = this.resolveTargetStock(entry, index, random, seedPlan.profile());
            targetStocks.put(entry.itemKey(), targetStock);
            if (targetStock > 0L) {
                positiveStockRows++;
            }
            final long currentStock = components.stockService().getSnapshot(entry.itemKey()).stockCount();
            if (targetStock > currentStock) {
                final long delta = targetStock - currentStock;
                this.addStockInChunks(components, entry.itemKey(), delta);
            } else if (currentStock > targetStock) {
                final long delta = currentStock - targetStock;
                this.removeStockInChunks(components, entry.itemKey(), delta);
            }
        }
        components.exchangeStockRepository().flushStocks(targetStocks);
        return new StockSeedSummary(entries.size(), positiveStockRows);
    }

    private long resolveTargetStock(
            final ExchangeCatalogEntry entry,
            final int index,
            final Random random,
            final TestProfile profile
    ) {
        final long cap = entry.stockCap();
        final int bucketSpan = switch (profile) {
            case SMOKE -> 6;
            case QA -> 8;
            case PERF, SOAK -> 10;
        };
        final int bucket = Math.floorMod(index, bucketSpan);

        if (cap <= 0L) {
            return switch (profile) {
                case SMOKE -> this.unboundedSmokeTarget(bucket, random);
                case QA -> this.unboundedQaTarget(bucket, random);
                case PERF, SOAK -> this.unboundedPerfTarget(bucket, random);
            };
        }

        return switch (profile) {
            case SMOKE -> this.boundedSmokeTarget(cap, bucket, random);
            case QA -> this.boundedQaTarget(cap, bucket, random);
            case PERF, SOAK -> this.boundedPerfTarget(cap, bucket, random);
        };
    }

    private long unboundedSmokeTarget(final int bucket, final Random random) {
        return switch (bucket) {
            case 0 -> 0L;
            case 1 -> 2L + random.nextInt(6);
            case 2 -> 32L + random.nextInt(96);
            case 3 -> 256L + random.nextInt(512);
            case 4 -> 1024L + random.nextInt(2048);
            default -> 48L + random.nextInt(128);
        };
    }

    private long unboundedQaTarget(final int bucket, final Random random) {
        return switch (bucket) {
            case 0 -> 1L + random.nextInt(4);
            case 1 -> 8L + random.nextInt(24);
            case 2 -> 32L + random.nextInt(96);
            case 3 -> 128L + random.nextInt(192);
            case 4 -> 256L + random.nextInt(384);
            case 5 -> 512L + random.nextInt(768);
            case 6 -> 1024L + random.nextInt(1024);
            default -> 64L + random.nextInt(128);
        };
    }

    private long unboundedPerfTarget(final int bucket, final Random random) {
        return switch (bucket) {
            case 0 -> 4L + random.nextInt(12);
            case 1 -> 16L + random.nextInt(48);
            case 2 -> 64L + random.nextInt(128);
            case 3 -> 192L + random.nextInt(256);
            case 4 -> 512L + random.nextInt(512);
            case 5 -> 1024L + random.nextInt(1024);
            case 6 -> 2048L + random.nextInt(1024);
            case 7 -> 4096L + random.nextInt(2048);
            case 8 -> 256L + random.nextInt(256);
            default -> 96L + random.nextInt(192);
        };
    }

    private long boundedSmokeTarget(final long cap, final int bucket, final Random random) {
        return switch (bucket) {
            case 0 -> 0L;
            case 1 -> Math.max(1L, Math.min(cap, 1L + random.nextInt((int) Math.max(2L, Math.min(cap, 4L)))));
            case 2 -> Math.max(1L, Math.round(cap * (0.20D + (random.nextDouble() * 0.15D))));
            case 3 -> Math.max(1L, Math.round(cap * (0.45D + (random.nextDouble() * 0.20D))));
            case 4 -> Math.max(1L, Math.round(cap * (0.85D + (random.nextDouble() * 0.40D))));
            default -> Math.max(1L, Math.round(cap * (0.10D + (random.nextDouble() * 0.10D))));
        };
    }

    private long boundedQaTarget(final long cap, final int bucket, final Random random) {
        return switch (bucket) {
            case 0 -> Math.max(1L, Math.round(cap * (0.03D + (random.nextDouble() * 0.04D))));
            case 1 -> Math.max(1L, Math.round(cap * (0.08D + (random.nextDouble() * 0.07D))));
            case 2 -> Math.max(1L, Math.round(cap * (0.18D + (random.nextDouble() * 0.10D))));
            case 3 -> Math.max(1L, Math.round(cap * (0.35D + (random.nextDouble() * 0.10D))));
            case 4 -> Math.max(1L, Math.round(cap * (0.55D + (random.nextDouble() * 0.10D))));
            case 5 -> Math.max(1L, Math.round(cap * (0.75D + (random.nextDouble() * 0.10D))));
            case 6 -> Math.max(1L, Math.round(cap * (0.92D + (random.nextDouble() * 0.08D))));
            default -> Math.max(1L, Math.round(cap * (0.12D + (random.nextDouble() * 0.06D))));
        };
    }

    private long boundedPerfTarget(final long cap, final int bucket, final Random random) {
        return switch (bucket) {
            case 0 -> Math.max(1L, Math.round(cap * (0.05D + (random.nextDouble() * 0.05D))));
            case 1 -> Math.max(1L, Math.round(cap * (0.12D + (random.nextDouble() * 0.08D))));
            case 2 -> Math.max(1L, Math.round(cap * (0.25D + (random.nextDouble() * 0.10D))));
            case 3 -> Math.max(1L, Math.round(cap * (0.45D + (random.nextDouble() * 0.10D))));
            case 4 -> Math.max(1L, Math.round(cap * (0.65D + (random.nextDouble() * 0.10D))));
            case 5 -> Math.max(1L, Math.round(cap * (0.82D + (random.nextDouble() * 0.08D))));
            case 6 -> Math.max(1L, Math.round(cap * (0.96D + (random.nextDouble() * 0.04D))));
            case 7 -> Math.max(1L, Math.round(cap * (1.05D + (random.nextDouble() * 0.08D))));
            case 8 -> Math.max(1L, Math.round(cap * (0.55D + (random.nextDouble() * 0.05D))));
            default -> Math.max(1L, Math.round(cap * (0.18D + (random.nextDouble() * 0.08D))));
        };
    }

    private int seedExchangeTransactions(
            final List<SeedPlayer> players,
            final List<ExchangeCatalogEntry> entries,
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan,
            final Random random
    ) {
        if (entries.isEmpty()) {
            return 0;
        }
        final int total = seedPlan.exchangeTransactionCount();
        for (int index = 0; index < total; index++) {
            final SeedPlayer player = players.get(random.nextInt(players.size()));
            final ExchangeCatalogEntry entry = entries.get(random.nextInt(entries.size()));
            final int amount = 1 + random.nextInt(32);
            final StockSnapshot snapshot = components.stockService().getSnapshot(entry.itemKey());
            final boolean buy = random.nextBoolean();
            final long createdAt = seedPlan.nowEpochSecond() - random.nextInt(86_400 * 7);
            final String metaJson = "{\"seeded\":true,\"profile\":\""
                    + seedPlan.profile().name().toLowerCase(Locale.ROOT)
                    + "\"}";
            if (buy) {
                final BuyQuote quote = components.pricingService().quoteBuy(entry.itemKey(), amount, snapshot);
                components.exchangeTransactionRepository().insert(
                        TransactionType.BUY,
                        player.playerId(),
                        entry.itemKey().value(),
                        amount,
                        quote.unitPrice(),
                        quote.totalPrice(),
                        Instant.ofEpochSecond(createdAt),
                        metaJson
                );
            } else {
                final SellQuote quote = components.pricingService().quoteSell(entry.itemKey(), amount, snapshot);
                components.exchangeTransactionRepository().insert(
                        TransactionType.SELL,
                        player.playerId(),
                        entry.itemKey().value(),
                        amount,
                        quote.effectiveUnitPrice(),
                        quote.totalPrice(),
                        Instant.ofEpochSecond(createdAt),
                        metaJson
                );
            }
        }
        return total;
    }

    private int seedSupplierContributions(
            final List<SeedPlayer> players,
            final List<ExchangeCatalogEntry> entries,
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan,
            final Random random
    ) {
        if (entries.isEmpty()) {
            return 0;
        }
        final String currentWeekKey = this.weekKey(seedPlan.nowEpochSecond());
        final int total = Math.max(1, seedPlan.exchangeTransactionCount() / 2);
        for (int index = 0; index < total; index++) {
            final SeedPlayer player = players.get(random.nextInt(players.size()));
            final ExchangeCatalogEntry entry = entries.get(random.nextInt(entries.size()));
            final int quantity = 1 + random.nextInt(48);
            final long updatedAt = seedPlan.nowEpochSecond() - random.nextInt(86_400 * 7);
            components.supplierStatsRepository().recordSaleContribution(
                    currentWeekKey,
                    player.playerId(),
                    entry.itemKey().value(),
                    quantity,
                    updatedAt
            );
        }
        return total;
    }

    private SeedStoreResult seedStore(
            final List<SeedPlayer> players,
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan,
            final Random random
    ) {
        final List<StoreProduct> products = new ArrayList<>(components.storeProductsConfig().products().values());
        if (products.isEmpty()) {
            return new SeedStoreResult(0, 0);
        }

        final Map<UUID, List<DirtyEntitlementGrant>> entitlementBatches = new LinkedHashMap<>();
        int entitlementCount = 0;
        for (int index = 0; index < seedPlan.entitlementGrantCount(); index++) {
            final SeedPlayer player = players.get(random.nextInt(players.size()));
            final StoreProduct product = products.get(random.nextInt(products.size()));
            if (product.entitlementKey() == null || product.entitlementKey().isBlank()) {
                continue;
            }
            final long grantedAt = seedPlan.nowEpochSecond() - random.nextInt(86_400 * 14);
            entitlementBatches
                    .computeIfAbsent(player.playerId(), ignored -> new ArrayList<>())
                    .add(new DirtyEntitlementGrant(product.entitlementKey(), product.productId(), grantedAt));
            entitlementCount++;
        }

        if (!entitlementBatches.isEmpty()) {
            components.transactionRunner().run(connection -> {
                for (final Map.Entry<UUID, List<DirtyEntitlementGrant>> entry : entitlementBatches.entrySet()) {
                    components.storeEntitlementRepository().upsertBatch(connection, entry.getKey(), entry.getValue());
                }
                return null;
            });
        }

        final List<StorePurchaseAuditRecord> purchases = new ArrayList<>();
        for (int index = 0; index < seedPlan.storePurchaseCount(); index++) {
            final SeedPlayer player = players.get(random.nextInt(players.size()));
            final StoreProduct product = products.get(random.nextInt(products.size()));
            final StorePurchaseStatus status = random.nextInt(100) < 92 ? StorePurchaseStatus.SUCCESS : StorePurchaseStatus.FAILED;
            final long createdAt = seedPlan.nowEpochSecond() - random.nextInt(86_400 * 14);
            purchases.add(new StorePurchaseAuditRecord(
                    player.playerId(),
                    product.productId(),
                    product.type(),
                    product.type() == StoreProductType.XP_WITHDRAWAL ? com.splatage.wild_economy.economy.model.MoneyAmount.zero() : product.price(),
                    status,
                    status == StorePurchaseStatus.SUCCESS ? null : "seeded-failure",
                    createdAt,
                    status == StorePurchaseStatus.SUCCESS ? createdAt + random.nextInt(300) : null
            ));
        }
        if (!purchases.isEmpty()) {
            components.transactionRunner().run(connection -> {
                components.storePurchaseRepository().insertBatch(connection, purchases);
                return null;
            });
        }
        return new SeedStoreResult(entitlementCount, purchases.size());
    }

    private List<ExchangeCatalogEntry> sortedCatalogEntries(final HarnessBootstrap.HarnessComponents components) {
        final List<ExchangeCatalogEntry> entries = new ArrayList<>(components.exchangeCatalog().allEntries());
        entries.sort(Comparator.comparing(entry -> entry.itemKey().value()));
        return entries;
    }

    private int countBuyEnabled(final List<ExchangeCatalogEntry> entries) {
        int count = 0;
        for (final ExchangeCatalogEntry entry : entries) {
            if (entry.buyEnabled()) {
                count++;
            }
        }
        return count;
    }

    private int countSellEnabled(final List<ExchangeCatalogEntry> entries) {
        int count = 0;
        for (final ExchangeCatalogEntry entry : entries) {
            if (entry.sellEnabled()) {
                count++;
            }
        }
        return count;
    }

    private int countPositiveSellQuoteEntries(
            final List<ExchangeCatalogEntry> entries,
            final HarnessBootstrap.HarnessComponents components
    ) {
        int count = 0;
        for (final ExchangeCatalogEntry entry : entries) {
            final SellQuote quote = components.pricingService().quoteSell(
                    entry.itemKey(),
                    1,
                    components.stockService().getSnapshot(entry.itemKey())
            );
            if (quote.totalPrice().signum() > 0) {
                count++;
            }
        }
        return count;
    }

    private void addStockInChunks(
            final HarnessBootstrap.HarnessComponents components,
            final ItemKey itemKey,
            final long amount
    ) {
        long remaining = amount;
        while (remaining > 0L) {
            final int chunk = (int) Math.min(Integer.MAX_VALUE, remaining);
            components.stockService().addStock(itemKey, chunk);
            remaining -= chunk;
        }
    }

    private void removeStockInChunks(
            final HarnessBootstrap.HarnessComponents components,
            final ItemKey itemKey,
            final long amount
    ) {
        long remaining = amount;
        while (remaining > 0L) {
            final int chunk = (int) Math.min(Integer.MAX_VALUE, remaining);
            components.stockService().removeStock(itemKey, chunk);
            remaining -= chunk;
        }
    }

    private String weekKey(final long epochSecond) {
        final java.time.LocalDate date = Instant.ofEpochSecond(epochSecond).atZone(ZoneOffset.UTC).toLocalDate();
        final WeekFields weekFields = WeekFields.ISO;
        final int year = date.get(weekFields.weekBasedYear());
        final int week = date.get(weekFields.weekOfWeekBasedYear());
        return "%04d-W%02d".formatted(year, week);
    }

    private record SeedStoreResult(int entitlementsSeeded, int purchasesSeeded) {
    }

    private record StockSeedSummary(int totalRowsSeeded, int rowsWithPositiveStock) {
    }
}
