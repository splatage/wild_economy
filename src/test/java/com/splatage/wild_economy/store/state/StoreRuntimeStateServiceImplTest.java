package com.splatage.wild_economy.store.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.persistence.DatabaseDialect;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.TransactionRunner;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StorePurchaseStatus;
import com.splatage.wild_economy.store.repository.StoreEntitlementRepository;
import com.splatage.wild_economy.store.repository.StorePurchaseRepository;
import com.splatage.wild_economy.store.repository.sqlite.SqliteStoreEntitlementRepository;
import com.splatage.wild_economy.store.repository.sqlite.SqliteStorePurchaseRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StoreRuntimeStateServiceImplTest {

    private static final String STORE_PREFIX = "test_";

    @TempDir
    Path tempDir;

    private StoreRuntimeStateServiceImpl runtimeStateService;
    private DatabaseProvider databaseProvider;

    @AfterEach
    void tearDown() {
        if (this.runtimeStateService != null) {
            this.runtimeStateService.shutdown();
        }
        if (this.databaseProvider != null) {
            this.databaseProvider.close();
        }
    }

    @Test
    void getOwnershipState_triggersLazyLoadAndResolvesOwnedState() throws Exception {
        this.createPersistenceHarness();
        final UUID playerId = UUID.randomUUID();
        this.insertEntitlement(playerId, "rank.vip", "vip_rank", 100L);

        this.runtimeStateService = this.createRuntimeStateService(
            new SqliteStoreEntitlementRepository(this.databaseProvider, STORE_PREFIX),
            new SqliteStorePurchaseRepository(STORE_PREFIX)
        );

        assertEquals(StoreOwnershipState.LOADING, this.runtimeStateService.getOwnershipState(playerId, "rank.vip"));

        this.waitUntil(Duration.ofSeconds(3),
            () -> this.runtimeStateService.getOwnershipState(playerId, "rank.vip") == StoreOwnershipState.OWNED);

        assertEquals(StoreOwnershipState.NOT_OWNED, this.runtimeStateService.getOwnershipState(playerId, "rank.staff"));
    }

    @Test
    void grantEntitlement_updatesMemoryImmediatelyAndPersistsDurabilityOnShutdown() throws Exception {
        this.createPersistenceHarness();
        final UUID playerId = UUID.randomUUID();

        this.runtimeStateService = this.createRuntimeStateService(
            new SqliteStoreEntitlementRepository(this.databaseProvider, STORE_PREFIX),
            new SqliteStorePurchaseRepository(STORE_PREFIX)
        );

        this.runtimeStateService.grantEntitlement(playerId, "rank.vip", "vip_rank", 1234L);
        this.runtimeStateService.recordPurchase(new StorePurchaseAuditRecord(
            playerId,
            "vip_rank",
            StoreProductType.PERMANENT_UNLOCK,
            MoneyAmount.ofMinor(1500L),
            StorePurchaseStatus.SUCCESS,
            null,
            1234L,
            1234L
        ));

        assertEquals(StoreOwnershipState.OWNED, this.runtimeStateService.getOwnershipState(playerId, "rank.vip"));

        this.runtimeStateService.shutdown();
        this.runtimeStateService = null;

        assertEquals(1L, this.countRows("SELECT COUNT(*) FROM " + STORE_PREFIX + "store_entitlements"));
        assertEquals(1L, this.countRows("SELECT COUNT(*) FROM " + STORE_PREFIX + "store_purchases"));
    }

    @Test
    void handlePlayerQuit_evictsCleanLoadedStateAndReloadsLazily() throws Exception {
        this.createPersistenceHarness();
        final UUID playerId = UUID.randomUUID();
        this.insertEntitlement(playerId, "rank.vip", "vip_rank", 200L);

        this.runtimeStateService = this.createRuntimeStateService(
            new SqliteStoreEntitlementRepository(this.databaseProvider, STORE_PREFIX),
            new SqliteStorePurchaseRepository(STORE_PREFIX)
        );

        this.waitUntil(Duration.ofSeconds(3), () -> {
            this.runtimeStateService.ensurePlayerLoadedAsync(playerId);
            return this.runtimeStateService.getOwnershipState(playerId, "rank.vip") == StoreOwnershipState.OWNED;
        });

        this.runtimeStateService.handlePlayerQuit(playerId);

        assertEquals(StoreOwnershipState.LOADING, this.runtimeStateService.getOwnershipState(playerId, "rank.vip"));
        this.waitUntil(Duration.ofSeconds(3),
            () -> this.runtimeStateService.getOwnershipState(playerId, "rank.vip") == StoreOwnershipState.OWNED);
    }

    @Test
    void handlePlayerQuit_retainsDirtyEntitlementWhenFlushFails() throws Exception {
        this.createPersistenceHarness();
        final UUID playerId = UUID.randomUUID();
        final StoreEntitlementRepository failingRepository = new StoreEntitlementRepository() {
            private final StoreEntitlementRepository delegate = new SqliteStoreEntitlementRepository(databaseProvider, STORE_PREFIX);

            @Override
            public Map<String, StoreEntitlementRecord> loadPlayerEntitlements(final UUID requestedPlayerId) {
                return this.delegate.loadPlayerEntitlements(requestedPlayerId);
            }

            @Override
            public void upsertBatch(
                final Connection connection,
                final UUID requestedPlayerId,
                final Collection<DirtyEntitlementGrant> grants
            ) {
                throw new IllegalStateException("boom");
            }
        };

        this.runtimeStateService = this.createRuntimeStateService(
            failingRepository,
            new SqliteStorePurchaseRepository(STORE_PREFIX)
        );

        this.runtimeStateService.grantEntitlement(playerId, "rank.vip", "vip_rank", 400L);
        this.runtimeStateService.handlePlayerQuit(playerId);

        this.waitUntil(Duration.ofSeconds(2),
            () -> this.runtimeStateService.getOwnershipState(playerId, "rank.vip") == StoreOwnershipState.OWNED);

        assertEquals(0L, this.countRows("SELECT COUNT(*) FROM " + STORE_PREFIX + "store_entitlements"));
    }

    private void createPersistenceHarness() throws Exception {
        final Path sqliteFile = this.tempDir.resolve("store-runtime-test.sqlite");
        this.databaseProvider = new DatabaseProvider(new DatabaseConfig(
            "sqlite",
            sqliteFile.toString(),
            null,
            0,
            null,
            null,
            null,
            false,
            2,
            "econ_",
            "exchange_",
            STORE_PREFIX
        ));
        try (Connection connection = this.databaseProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS test_store_entitlements (
                    player_uuid TEXT NOT NULL,
                    entitlement_key TEXT NOT NULL,
                    product_id TEXT NOT NULL,
                    granted_at INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, entitlement_key)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS test_store_purchases (
                    purchase_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    product_id TEXT NOT NULL,
                    product_type TEXT NOT NULL,
                    price_minor INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    failure_reason TEXT NULL,
                    created_at INTEGER NOT NULL,
                    completed_at INTEGER NULL
                )
                """);
            connection.commit();
        }
    }

    private StoreRuntimeStateServiceImpl createRuntimeStateService(
        final StoreEntitlementRepository entitlementRepository,
        final StorePurchaseRepository purchaseRepository
    ) {
        return new StoreRuntimeStateServiceImpl(
            entitlementRepository,
            purchaseRepository,
            new TransactionRunner(this.databaseProvider),
            Logger.getLogger(StoreRuntimeStateServiceImplTest.class.getName()),
            DatabaseDialect.SQLITE,
            2
        );
    }

    private void insertEntitlement(
        final UUID playerId,
        final String entitlementKey,
        final String productId,
        final long grantedAtEpochSecond
    ) throws Exception {
        try (Connection connection = this.databaseProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "INSERT INTO " + STORE_PREFIX + "store_entitlements (player_uuid, entitlement_key, product_id, granted_at) VALUES ('"
                    + playerId + "', '" + entitlementKey + "', '" + productId + "', " + grantedAtEpochSecond + ")"
            );
            connection.commit();
        }
    }

    private long countRows(final String sql) throws Exception {
        try (Connection connection = this.databaseProvider.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            connection.commit();
            return resultSet.getLong(1);
        }
    }

    private void waitUntil(final Duration timeout, final BooleanSupplier condition) throws Exception {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25L);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not satisfied before timeout");
    }
}
