package com.splatage.wild_economy.store.state;

import com.splatage.wild_economy.persistence.DatabaseDialect;
import com.splatage.wild_economy.persistence.TransactionRunner;
import com.splatage.wild_economy.store.repository.StoreEntitlementRepository;
import com.splatage.wild_economy.store.repository.StorePurchaseRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StoreRuntimeStateServiceImpl implements StoreRuntimeStateService {

    private static final long FLUSH_INTERVAL_MILLIS = 2_000L;
    private static final int SQLITE_QUEUE_CAPACITY = 1_024;
    private static final int MYSQL_QUEUE_CAPACITY = 4_096;

    private final StoreEntitlementRepository storeEntitlementRepository;
    private final StorePurchaseRepository storePurchaseRepository;
    private final TransactionRunner transactionRunner;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, PlayerStoreState> playerStates;
    private final ConcurrentLinkedQueue<StorePurchaseAuditRecord> purchaseAuditQueue;
    private final ThreadPoolExecutor persistenceExecutor;
    private final ScheduledExecutorService flushScheduler;
    private final AtomicBoolean flushDispatchInProgress;

    public StoreRuntimeStateServiceImpl(
        final StoreEntitlementRepository storeEntitlementRepository,
        final StorePurchaseRepository storePurchaseRepository,
        final TransactionRunner transactionRunner,
        final Logger logger,
        final DatabaseDialect dialect,
        final int mysqlMaximumPoolSize
    ) {
        this.storeEntitlementRepository = Objects.requireNonNull(storeEntitlementRepository, "storeEntitlementRepository");
        this.storePurchaseRepository = Objects.requireNonNull(storePurchaseRepository, "storePurchaseRepository");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.playerStates = new ConcurrentHashMap<>();
        this.purchaseAuditQueue = new ConcurrentLinkedQueue<>();
        this.persistenceExecutor = this.createExecutor(dialect, mysqlMaximumPoolSize);
        this.flushScheduler = this.createFlushScheduler(dialect);
        this.flushDispatchInProgress = new AtomicBoolean(false);
        this.startFlushScheduler();
    }

    @Override
    public void ensurePlayerLoadedAsync(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        final PlayerStoreState state = this.playerStates.computeIfAbsent(playerId, ignored -> new PlayerStoreState());
        state.markActive();
        if (!state.beginLoad()) {
            return;
        }
        try {
            this.persistenceExecutor.submit(() -> this.loadPlayerState(playerId, state));
        } catch (final RejectedExecutionException exception) {
            state.failLoad();
            this.logger.log(Level.WARNING, "Store runtime state queue rejected player entitlement load for " + playerId, exception);
        }
    }

    @Override
    public StoreOwnershipState getOwnershipState(final UUID playerId, final String entitlementKey) {
        Objects.requireNonNull(playerId, "playerId");
        if (entitlementKey == null || entitlementKey.isBlank()) {
            return StoreOwnershipState.NOT_OWNED;
        }

        final PlayerStoreState state = this.playerStates.computeIfAbsent(playerId, ignored -> new PlayerStoreState());
        state.markActive();
        final PlayerLoadState loadState = state.loadState();
        if (loadState == PlayerLoadState.UNLOADED) {
            this.ensurePlayerLoadedAsync(playerId);
            return StoreOwnershipState.LOADING;
        }
        if (loadState == PlayerLoadState.LOADING) {
            return StoreOwnershipState.LOADING;
        }
        if (loadState == PlayerLoadState.LOAD_FAILED) {
            return StoreOwnershipState.LOAD_FAILED;
        }
        return state.hasEntitlement(entitlementKey) ? StoreOwnershipState.OWNED : StoreOwnershipState.NOT_OWNED;
    }

    @Override
    public void grantEntitlement(
        final UUID playerId,
        final String entitlementKey,
        final String productId,
        final long grantedAtEpochSecond
    ) {
        Objects.requireNonNull(playerId, "playerId");
        if (entitlementKey == null || entitlementKey.isBlank()) {
            return;
        }
        final PlayerStoreState state = this.playerStates.computeIfAbsent(playerId, ignored -> new PlayerStoreState());
        state.markActive();
        state.applyGrant(entitlementKey, productId, grantedAtEpochSecond);
        this.flushDirtyNow();
    }

    @Override
    public void recordPurchase(final StorePurchaseAuditRecord record) {
        Objects.requireNonNull(record, "record");
        this.purchaseAuditQueue.add(record);
        this.flushDirtyNow();
    }

    @Override
    public void handlePlayerQuit(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        final PlayerStoreState state = this.playerStates.get(playerId);
        if (state == null) {
            return;
        }
        state.markQuitRequested();
        if (state.hasDirtyEntitlements()) {
            this.flushDirtyNow();
            return;
        }
        if (state.canEvictNow()) {
            this.playerStates.remove(playerId, state);
        }
    }

    @Override
    public void flushDirtyNow() {
        if (!this.hasPendingWork()) {
            return;
        }
        if (!this.flushDispatchInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            this.persistenceExecutor.submit(this::flushPendingWork);
        } catch (final RejectedExecutionException exception) {
            this.flushDispatchInProgress.set(false);
            this.logger.log(Level.WARNING, "Store runtime state queue rejected a flush task. Dirty state will remain pending.", exception);
        }
    }

    @Override
    public void shutdown() {
        this.flushScheduler.shutdown();
        try {
            if (!this.flushScheduler.awaitTermination(5L, TimeUnit.SECONDS)) {
                this.flushScheduler.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.flushScheduler.shutdownNow();
        }

        this.flushDirtyNow();
        this.persistenceExecutor.shutdown();
        try {
            if (!this.persistenceExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                this.persistenceExecutor.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.persistenceExecutor.shutdownNow();
        }

        this.flushDirtySynchronously();
    }

    private void loadPlayerState(final UUID playerId, final PlayerStoreState state) {
        try {
            final Map<String, StoreEntitlementRecord> records = this.storeEntitlementRepository.loadPlayerEntitlements(playerId);
            state.completeLoad(records.keySet());
        } catch (final RuntimeException exception) {
            state.failLoad();
            this.logger.log(Level.WARNING, "Failed to lazy-load store entitlements for " + playerId, exception);
        } finally {
            this.evictIdlePlayer(playerId, state);
        }
    }

    private boolean hasPendingWork() {
        return this.hasPendingEntitlements() || !this.purchaseAuditQueue.isEmpty();
    }

    private boolean hasPendingEntitlements() {
        for (final PlayerStoreState state : this.playerStates.values()) {
            if (state.hasDirtyEntitlements()) {
                return true;
            }
        }
        return false;
    }

    private void flushPendingWork() {
        boolean entitlementFlushed = false;
        boolean purchaseFlushed = false;
        try {
            entitlementFlushed = this.flushPendingEntitlementsAsync();
            purchaseFlushed = this.flushPendingPurchaseAuditAsync();
        } finally {
            this.flushDispatchInProgress.set(false);
            final boolean pendingEntitlements = this.hasPendingEntitlements();
            final boolean pendingPurchaseAudit = !this.purchaseAuditQueue.isEmpty();
            if ((entitlementFlushed && pendingEntitlements) || (purchaseFlushed && pendingPurchaseAudit)) {
                this.flushDirtyNow();
            }
        }
    }

    private void flushDirtySynchronously() {
        while (this.hasPendingWork()) {
            boolean madeProgress = false;
            madeProgress |= this.flushPendingEntitlementsSync();
            madeProgress |= this.flushPendingPurchaseAuditSync();
            if (!madeProgress) {
                if (this.hasPendingEntitlements()) {
                    this.logger.severe("Store runtime shutdown ended with entitlement writes still pending.");
                }
                if (!this.purchaseAuditQueue.isEmpty()) {
                    this.logger.severe("Store runtime shutdown ended with purchase audit writes still pending.");
                }
                return;
            }
        }
    }

    private boolean flushPendingEntitlementsAsync() {
        final Map<UUID, List<DirtyEntitlementGrant>> entitlementSnapshot = this.snapshotDirtyEntitlements();
        if (entitlementSnapshot.isEmpty()) {
            return false;
        }
        try {
            this.transactionRunner.run(connection -> {
                for (final Map.Entry<UUID, List<DirtyEntitlementGrant>> entry : entitlementSnapshot.entrySet()) {
                    this.storeEntitlementRepository.upsertBatch(connection, entry.getKey(), entry.getValue());
                }
                return null;
            });
            this.markSuccessfulEntitlementFlush(entitlementSnapshot);
            this.evictFlushedPlayers(entitlementSnapshot.keySet());
            return true;
        } catch (final RuntimeException exception) {
            this.logger.log(Level.WARNING, "Failed to flush pending store entitlements.", exception);
            return false;
        }
    }

    private boolean flushPendingPurchaseAuditAsync() {
        final List<StorePurchaseAuditRecord> purchaseSnapshot = this.drainPurchaseAuditQueue();
        if (purchaseSnapshot.isEmpty()) {
            return false;
        }
        try {
            this.transactionRunner.run(connection -> {
                this.storePurchaseRepository.insertBatch(connection, purchaseSnapshot);
                return null;
            });
            return true;
        } catch (final RuntimeException exception) {
            this.restorePurchaseAuditQueue(purchaseSnapshot);
            this.logger.log(Level.WARNING, "Failed to flush pending store purchase audit.", exception);
            return false;
        }
    }

    private boolean flushPendingEntitlementsSync() {
        final Map<UUID, List<DirtyEntitlementGrant>> entitlementSnapshot = this.snapshotDirtyEntitlements();
        if (entitlementSnapshot.isEmpty()) {
            return false;
        }
        try {
            this.transactionRunner.run(connection -> {
                for (final Map.Entry<UUID, List<DirtyEntitlementGrant>> entry : entitlementSnapshot.entrySet()) {
                    this.storeEntitlementRepository.upsertBatch(connection, entry.getKey(), entry.getValue());
                }
                return null;
            });
            this.markSuccessfulEntitlementFlush(entitlementSnapshot);
            this.evictFlushedPlayers(entitlementSnapshot.keySet());
            return true;
        } catch (final RuntimeException exception) {
            this.logger.log(Level.SEVERE, "Failed to synchronously flush store entitlements during shutdown.", exception);
            return false;
        }
    }

    private boolean flushPendingPurchaseAuditSync() {
        final List<StorePurchaseAuditRecord> purchaseSnapshot = this.drainPurchaseAuditQueue();
        if (purchaseSnapshot.isEmpty()) {
            return false;
        }
        try {
            this.transactionRunner.run(connection -> {
                this.storePurchaseRepository.insertBatch(connection, purchaseSnapshot);
                return null;
            });
            return true;
        } catch (final RuntimeException exception) {
            this.restorePurchaseAuditQueue(purchaseSnapshot);
            this.logger.log(Level.SEVERE, "Failed to synchronously flush store purchase audit during shutdown.", exception);
            return false;
        }
    }

    private Map<UUID, List<DirtyEntitlementGrant>> snapshotDirtyEntitlements() {
        final Map<UUID, List<DirtyEntitlementGrant>> snapshot = new LinkedHashMap<>();
        for (final Map.Entry<UUID, PlayerStoreState> entry : this.playerStates.entrySet()) {
            final List<DirtyEntitlementGrant> dirtyGrants = entry.getValue().dirtyEntitlementsSnapshot();
            if (!dirtyGrants.isEmpty()) {
                snapshot.put(entry.getKey(), dirtyGrants);
            }
        }
        return snapshot;
    }

    private List<StorePurchaseAuditRecord> drainPurchaseAuditQueue() {
        final List<StorePurchaseAuditRecord> drained = new ArrayList<>();
        StorePurchaseAuditRecord record;
        while ((record = this.purchaseAuditQueue.poll()) != null) {
            drained.add(record);
        }
        return drained;
    }

    private void restorePurchaseAuditQueue(final Collection<StorePurchaseAuditRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        for (final StorePurchaseAuditRecord record : records) {
            this.purchaseAuditQueue.add(record);
        }
    }

    private void markSuccessfulEntitlementFlush(final Map<UUID, List<DirtyEntitlementGrant>> entitlementSnapshot) {
        for (final Map.Entry<UUID, List<DirtyEntitlementGrant>> entry : entitlementSnapshot.entrySet()) {
            final PlayerStoreState state = this.playerStates.get(entry.getKey());
            if (state != null) {
                state.markFlushed(entry.getValue());
            }
        }
    }

    private void evictFlushedPlayers(final Collection<UUID> playerIds) {
        for (final UUID playerId : playerIds) {
            final PlayerStoreState state = this.playerStates.get(playerId);
            if (state != null) {
                this.evictIdlePlayer(playerId, state);
            }
        }
    }

    private void evictIdlePlayer(final UUID playerId, final PlayerStoreState state) {
        if (state.canEvictWhenInactive()) {
            this.playerStates.remove(playerId, state);
        }
    }

    private ThreadPoolExecutor createExecutor(final DatabaseDialect dialect, final int mysqlMaximumPoolSize) {
        final int workers = dialect == DatabaseDialect.SQLITE ? 1 : Math.max(2, Math.min(4, mysqlMaximumPoolSize));
        final int queueCapacity = dialect == DatabaseDialect.SQLITE ? SQLITE_QUEUE_CAPACITY : MYSQL_QUEUE_CAPACITY;
        final String threadPrefix = dialect == DatabaseDialect.SQLITE ? "wild-economy-store-sqlite" : "wild-economy-store-mysql";
        final AtomicInteger threadCounter = new AtomicInteger(1);

        return new ThreadPoolExecutor(
            workers,
            workers,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            runnable -> {
                final Thread thread = new Thread(runnable, threadPrefix + "-" + threadCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        );
    }

    private ScheduledExecutorService createFlushScheduler(final DatabaseDialect dialect) {
        final String threadName = dialect == DatabaseDialect.SQLITE
            ? "wild-economy-store-flush-sqlite"
            : "wild-economy-store-flush-mysql";
        return java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    private void startFlushScheduler() {
        this.flushScheduler.scheduleAtFixedRate(
            this::safePeriodicFlush,
            FLUSH_INTERVAL_MILLIS,
            FLUSH_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS
        );
    }

    private void safePeriodicFlush() {
        try {
            this.flushDirtyNow();
        } catch (final RuntimeException exception) {
            this.logger.log(Level.WARNING, "Periodic store runtime state flush failed.", exception);
        }
    }

    private static final class PlayerStoreState {

        private final Set<String> entitlementKeys;
        private final ConcurrentHashMap<String, DirtyEntitlementGrant> dirtyEntitlements;
        private volatile PlayerLoadState loadState;
        private volatile boolean quitRequested;

        private PlayerStoreState() {
            this.entitlementKeys = ConcurrentHashMap.newKeySet();
            this.dirtyEntitlements = new ConcurrentHashMap<>();
            this.loadState = PlayerLoadState.UNLOADED;
            this.quitRequested = false;
        }

        private synchronized boolean beginLoad() {
            if (this.loadState == PlayerLoadState.LOADING || this.loadState == PlayerLoadState.LOADED) {
                return false;
            }
            this.loadState = PlayerLoadState.LOADING;
            return true;
        }

        private synchronized void completeLoad(final Collection<String> loadedEntitlements) {
            this.entitlementKeys.clear();
            this.entitlementKeys.addAll(loadedEntitlements);
            this.entitlementKeys.addAll(this.dirtyEntitlements.keySet());
            this.loadState = PlayerLoadState.LOADED;
        }

        private void failLoad() {
            this.loadState = PlayerLoadState.LOAD_FAILED;
        }

        private void markActive() {
            this.quitRequested = false;
        }

        private void markQuitRequested() {
            this.quitRequested = true;
        }

        private PlayerLoadState loadState() {
            return this.loadState;
        }

        private boolean hasEntitlement(final String entitlementKey) {
            return this.entitlementKeys.contains(entitlementKey);
        }

        private void applyGrant(final String entitlementKey, final String productId, final long grantedAtEpochSecond) {
            this.entitlementKeys.add(entitlementKey);
            this.dirtyEntitlements.put(entitlementKey, new DirtyEntitlementGrant(entitlementKey, productId, grantedAtEpochSecond));
            this.loadState = PlayerLoadState.LOADED;
        }

        private boolean hasDirtyEntitlements() {
            return !this.dirtyEntitlements.isEmpty();
        }

        private List<DirtyEntitlementGrant> dirtyEntitlementsSnapshot() {
            return List.copyOf(this.dirtyEntitlements.values());
        }

        private void markFlushed(final Collection<DirtyEntitlementGrant> flushedGrants) {
            for (final DirtyEntitlementGrant grant : flushedGrants) {
                this.dirtyEntitlements.remove(grant.entitlementKey(), grant);
            }
        }

        private boolean canEvictNow() {
            return this.loadState != PlayerLoadState.LOADING && this.dirtyEntitlements.isEmpty();
        }

        private boolean canEvictWhenInactive() {
            return this.quitRequested && this.canEvictNow();
        }
    }
}
