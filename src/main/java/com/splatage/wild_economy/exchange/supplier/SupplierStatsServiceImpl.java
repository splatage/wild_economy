package com.splatage.wild_economy.exchange.supplier;

import com.splatage.wild_economy.economy.repository.EconomyNameCacheRepository;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.repository.SupplierStatsRepository;
import com.splatage.wild_economy.persistence.DatabaseDialect;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SupplierStatsServiceImpl implements SupplierStatsService {

    private static final int SQLITE_QUEUE_CAPACITY = 4096;
    private static final int MYSQL_QUEUE_CAPACITY = 8192;

    private final SupplierStatsRepository supplierStatsRepository;
    private final EconomyNameCacheRepository economyNameCacheRepository;
    private final ExchangeCatalog exchangeCatalog;
    private final Logger logger;
    private final ExecutorService executor;
    private final ConcurrentMap<UUID, Long> allTimeTotals = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<ItemKey, Long>> allTimeByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> weeklyTotals = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<ItemKey, Long>> weeklyByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> knownNames = new ConcurrentHashMap<>();
    private volatile String currentWeekKey;

    public SupplierStatsServiceImpl(
        final SupplierStatsRepository supplierStatsRepository,
        final EconomyNameCacheRepository economyNameCacheRepository,
        final ExchangeCatalog exchangeCatalog,
        final Logger logger,
        final DatabaseDialect databaseDialect,
        final int mysqlMaximumPoolSize
    ) {
        this.supplierStatsRepository = Objects.requireNonNull(supplierStatsRepository, "supplierStatsRepository");
        this.economyNameCacheRepository = Objects.requireNonNull(economyNameCacheRepository, "economyNameCacheRepository");
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = this.createExecutor(databaseDialect, mysqlMaximumPoolSize);
        this.currentWeekKey = this.weekKey(Instant.now());
        this.loadSnapshots();
    }

    @Override
    public void recordSale(final UUID playerId, final ItemKey itemKey, final int amount) {
        if (playerId == null || itemKey == null || amount <= 0) {
            return;
        }

        this.rotateWeekIfNeeded();
        this.allTimeTotals.merge(playerId, (long) amount, Long::sum);
        this.allTimeByPlayer.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).merge(itemKey, (long) amount, Long::sum);
        this.weeklyTotals.merge(playerId, (long) amount, Long::sum);
        this.weeklyByPlayer.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).merge(itemKey, (long) amount, Long::sum);
        this.capturePlayerName(playerId);

        final String weekKey = this.currentWeekKey;
        final long updatedAtEpochSecond = Instant.now().getEpochSecond();
        try {
            this.executor.submit(() -> {
                try {
                    this.supplierStatsRepository.recordSaleContribution(weekKey, playerId, itemKey.value(), amount, updatedAtEpochSecond);
                } catch (final RuntimeException exception) {
                    this.logger.log(Level.WARNING, "Failed to persist supplier aggregate for " + itemKey.value() + '.', exception);
                }
            });
        } catch (final RejectedExecutionException exception) {
            this.logger.log(Level.WARNING, "Supplier aggregate queue rejected update for " + itemKey.value() + '.', exception);
        }
    }

    @Override
    public List<TopSupplierEntry> getTopSuppliers(final SupplierScope scope, final int limit) {
        this.rotateWeekIfNeeded();
        final int safeLimit = Math.max(1, limit);
        final List<Map.Entry<UUID, Long>> sorted = this.sortedTotals(this.totalsFor(scope));
        final List<TopSupplierEntry> entries = new ArrayList<>(Math.min(safeLimit, sorted.size()));
        for (int index = 0; index < sorted.size() && index < safeLimit; index++) {
            final Map.Entry<UUID, Long> entry = sorted.get(index);
            entries.add(new TopSupplierEntry(index + 1, entry.getKey(), this.resolveDisplayName(entry.getKey()), entry.getValue()));
        }
        return List.copyOf(entries);
    }

    @Override
    public Optional<SupplierPlayerDetail> getPlayerDetail(final SupplierScope scope, final UUID playerId, final int topItemsLimit) {
        this.rotateWeekIfNeeded();
        final Map<UUID, Long> totals = this.totalsFor(scope);
        final long totalQuantitySold = totals.getOrDefault(playerId, 0L);
        if (totalQuantitySold <= 0L) {
            return Optional.empty();
        }

        final Map<ItemKey, Long> contributions = this.breakdownFor(scope).getOrDefault(playerId, new ConcurrentHashMap<>());
        final int safeLimit = Math.max(1, topItemsLimit);
        final List<SupplierContributionEntry> topContributions = contributions.entrySet().stream()
            .filter(entry -> entry.getValue() > 0L)
            .sorted(Map.Entry.<ItemKey, Long>comparingByValue().reversed().thenComparing(entry -> entry.getKey().value(), String.CASE_INSENSITIVE_ORDER))
            .limit(safeLimit)
            .map(entry -> new SupplierContributionEntry(
                entry.getKey(),
                this.displayName(entry.getKey()),
                entry.getValue()
            ))
            .toList();

        final int rank = this.computeRank(totals, playerId);
        return Optional.of(new SupplierPlayerDetail(
            scope,
            playerId,
            this.resolveDisplayName(playerId),
            rank,
            totalQuantitySold,
            List.copyOf(topContributions)
        ));
    }

    @Override
    public Optional<UUID> findPlayerIdByName(final String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }

        for (final Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getName().equalsIgnoreCase(playerName)) {
                this.knownNames.put(onlinePlayer.getUniqueId(), onlinePlayer.getName());
                return Optional.of(onlinePlayer.getUniqueId());
            }
        }

        return this.knownNames.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue().equalsIgnoreCase(playerName))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    @Override
    public void shutdown() {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.executor.shutdownNow();
        }
    }

    private void loadSnapshots() {
        this.loadRows(this.supplierStatsRepository.loadAllTimeRows(), this.allTimeTotals, this.allTimeByPlayer);
        this.loadRows(this.supplierStatsRepository.loadWeeklyRows(this.currentWeekKey), this.weeklyTotals, this.weeklyByPlayer);
        final Set<UUID> playerIds = new java.util.HashSet<>();
        playerIds.addAll(this.allTimeTotals.keySet());
        playerIds.addAll(this.weeklyTotals.keySet());
        this.knownNames.putAll(this.economyNameCacheRepository.findLastKnownNames(playerIds));
    }

    private void loadRows(
        final List<SupplierAggregateRow> rows,
        final ConcurrentMap<UUID, Long> totals,
        final ConcurrentMap<UUID, ConcurrentMap<ItemKey, Long>> breakdown
    ) {
        for (final SupplierAggregateRow row : rows) {
            final ItemKey itemKey = new ItemKey(row.itemKey());
            totals.merge(row.playerId(), row.quantitySold(), Long::sum);
            breakdown.computeIfAbsent(row.playerId(), ignored -> new ConcurrentHashMap<>()).merge(itemKey, row.quantitySold(), Long::sum);
        }
    }

    private void rotateWeekIfNeeded() {
        final String resolvedWeekKey = this.weekKey(Instant.now());
        if (resolvedWeekKey.equals(this.currentWeekKey)) {
            return;
        }

        synchronized (this) {
            if (resolvedWeekKey.equals(this.currentWeekKey)) {
                return;
            }
            this.currentWeekKey = resolvedWeekKey;
            this.weeklyTotals.clear();
            this.weeklyByPlayer.clear();
            this.loadRows(this.supplierStatsRepository.loadWeeklyRows(this.currentWeekKey), this.weeklyTotals, this.weeklyByPlayer);
            this.knownNames.putAll(this.economyNameCacheRepository.findLastKnownNames(this.weeklyTotals.keySet()));
        }
    }

    private Map<UUID, Long> totalsFor(final SupplierScope scope) {
        return scope == SupplierScope.ALL_TIME ? this.allTimeTotals : this.weeklyTotals;
    }

    private ConcurrentMap<UUID, ConcurrentMap<ItemKey, Long>> breakdownFor(final SupplierScope scope) {
        return scope == SupplierScope.ALL_TIME ? this.allTimeByPlayer : this.weeklyByPlayer;
    }

    private int computeRank(final Map<UUID, Long> totals, final UUID playerId) {
        final long playerTotal = totals.getOrDefault(playerId, 0L);
        if (playerTotal <= 0L) {
            return 0;
        }
        final List<Map.Entry<UUID, Long>> sorted = this.sortedTotals(totals);
        for (int index = 0; index < sorted.size(); index++) {
            if (sorted.get(index).getKey().equals(playerId)) {
                return index + 1;
            }
        }
        return 0;
    }

    private List<Map.Entry<UUID, Long>> sortedTotals(final Map<UUID, Long> totals) {
        final List<Map.Entry<UUID, Long>> sorted = new ArrayList<>();
        for (final Map.Entry<UUID, Long> entry : totals.entrySet()) {
            if (entry.getValue() > 0L) {
                sorted.add(entry);
            }
        }
        sorted.sort((left, right) -> {
            final int quantityComparison = Long.compare(right.getValue(), left.getValue());
            if (quantityComparison != 0) {
                return quantityComparison;
            }
            return this.resolveDisplayName(left.getKey()).compareToIgnoreCase(this.resolveDisplayName(right.getKey()));
        });
        return sorted;
    }

    private String resolveDisplayName(final UUID playerId) {
        final Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) {
            this.knownNames.put(playerId, onlinePlayer.getName());
            return onlinePlayer.getName();
        }
        return this.knownNames.getOrDefault(playerId, playerId.toString());
    }

    private void capturePlayerName(final UUID playerId) {
        final Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) {
            this.knownNames.put(playerId, onlinePlayer.getName());
        }
    }

    private String displayName(final ItemKey itemKey) {
        final Optional<ExchangeCatalogEntry> entry = this.exchangeCatalog.get(itemKey);
        return entry.map(ExchangeCatalogEntry::displayName).orElse(itemKey.value());
    }

    private String weekKey(final Instant instant) {
        final java.time.LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        final WeekFields weekFields = WeekFields.ISO;
        final int weekBasedYear = date.get(weekFields.weekBasedYear());
        final int week = date.get(weekFields.weekOfWeekBasedYear());
        return "%04d-W%02d".formatted(weekBasedYear, week);
    }

    private ExecutorService createExecutor(final DatabaseDialect dialect, final int mysqlMaximumPoolSize) {
        final int workers = dialect == DatabaseDialect.SQLITE ? 1 : Math.max(2, Math.min(4, mysqlMaximumPoolSize));
        final int queueCapacity = dialect == DatabaseDialect.SQLITE ? SQLITE_QUEUE_CAPACITY : MYSQL_QUEUE_CAPACITY;
        final String threadPrefix = dialect == DatabaseDialect.SQLITE ? "wild-economy-supplier-sqlite" : "wild-economy-supplier-mysql";
        final AtomicInteger threadCounter = new AtomicInteger(1);
        return new ThreadPoolExecutor(
            workers,
            workers,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            runnable -> {
                final Thread thread = new Thread(runnable, threadPrefix + '-' + threadCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        );
    }
}
