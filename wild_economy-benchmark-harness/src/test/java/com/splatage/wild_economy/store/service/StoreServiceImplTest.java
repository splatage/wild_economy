package com.splatage.wild_economy.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.config.StoreProductsConfig;
import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.EconomyTransferResult;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.store.action.ProductActionExecutor;
import com.splatage.wild_economy.store.action.StoreActionExecutionResult;
import com.splatage.wild_economy.store.model.StoreAction;
import com.splatage.wild_economy.store.model.StoreActionType;
import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StorePurchaseResult;
import com.splatage.wild_economy.store.model.StorePurchaseStatus;
import com.splatage.wild_economy.store.state.StoreOwnershipState;
import com.splatage.wild_economy.store.state.StorePurchaseAuditRecord;
import com.splatage.wild_economy.store.state.StoreRuntimeStateService;
import com.splatage.wild_economy.xp.service.XpBottleService;
import com.splatage.wild_economy.xp.service.XpBottleWithdrawResult;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class StoreServiceImplTest {

    @Test
    void purchase_permanentUnlockRejectsAlreadyOwnedWithoutMutatingEconomy() {
        final FakeEconomyService economyService = new FakeEconomyService(MoneyAmount.ofMinor(5_000L));
        final FakeStoreRuntimeStateService runtimeStateService = new FakeStoreRuntimeStateService();
        runtimeStateService.ownershipState = StoreOwnershipState.OWNED;
        final FakeProductActionExecutor actionExecutor = new FakeProductActionExecutor(StoreActionExecutionResult.succeed());
        final FakeXpBottleService xpBottleService = new FakeXpBottleService(XpBottleWithdrawResult.success(100, 0));
        final StoreServiceImpl service = new StoreServiceImpl(
            this.config(this.permanentUnlock("vip_rank", MoneyAmount.ofMinor(1_500L))),
            economyService,
            runtimeStateService,
            actionExecutor,
            xpBottleService
        );

        final StorePurchaseResult result = service.purchase(this.player(UUID.randomUUID()), "vip_rank");

        assertFalse(result.success());
        assertEquals(0, economyService.withdrawCalls);
        assertEquals(0, actionExecutor.calls);
        assertTrue(runtimeStateService.purchaseRecords.isEmpty());
    }

    @Test
    void purchase_permanentUnlockRejectsWhileOwnershipLoading() {
        final FakeEconomyService economyService = new FakeEconomyService(MoneyAmount.ofMinor(5_000L));
        final FakeStoreRuntimeStateService runtimeStateService = new FakeStoreRuntimeStateService();
        runtimeStateService.ownershipState = StoreOwnershipState.LOADING;
        final StoreServiceImpl service = new StoreServiceImpl(
            this.config(this.permanentUnlock("vip_rank", MoneyAmount.ofMinor(1_500L))),
            economyService,
            runtimeStateService,
            new FakeProductActionExecutor(StoreActionExecutionResult.succeed()),
            new FakeXpBottleService(XpBottleWithdrawResult.success(100, 0))
        );

        final StorePurchaseResult result = service.purchase(this.player(UUID.randomUUID()), "vip_rank");

        assertFalse(result.success());
        assertEquals(0, economyService.withdrawCalls);
        assertEquals(0, runtimeStateService.ensureCalls);
        assertTrue(result.message().contains("loading"));
    }

    @Test
    void purchase_permanentUnlockSuccessWithdrawsExecutesAndGrantsEntitlement() {
        final FakeEconomyService economyService = new FakeEconomyService(MoneyAmount.ofMinor(5_000L));
        final FakeStoreRuntimeStateService runtimeStateService = new FakeStoreRuntimeStateService();
        runtimeStateService.ownershipState = StoreOwnershipState.NOT_OWNED;
        final FakeProductActionExecutor actionExecutor = new FakeProductActionExecutor(StoreActionExecutionResult.succeed());
        final StoreServiceImpl service = new StoreServiceImpl(
            this.config(this.permanentUnlock("vip_rank", MoneyAmount.ofMinor(1_500L))),
            economyService,
            runtimeStateService,
            actionExecutor,
            new FakeXpBottleService(XpBottleWithdrawResult.success(100, 0))
        );
        final UUID playerId = UUID.randomUUID();

        final StorePurchaseResult result = service.purchase(this.player(playerId), "vip_rank");

        assertTrue(result.success());
        assertEquals(1, economyService.withdrawCalls);
        assertEquals(0, economyService.depositCalls);
        assertEquals(1, actionExecutor.calls);
        assertEquals(playerId, runtimeStateService.lastGrantedPlayerId);
        assertEquals("rank.vip", runtimeStateService.lastGrantedEntitlementKey);
        assertEquals(1, runtimeStateService.purchaseRecords.size());
        assertEquals(StorePurchaseStatus.SUCCESS, runtimeStateService.purchaseRecords.getFirst().status());
        assertNull(runtimeStateService.purchaseRecords.getFirst().failureReason());
    }

    @Test
    void purchase_actionFailureRefundsAndRecordsRefundedWithoutGrant() {
        final FakeEconomyService economyService = new FakeEconomyService(MoneyAmount.ofMinor(5_000L));
        final FakeStoreRuntimeStateService runtimeStateService = new FakeStoreRuntimeStateService();
        runtimeStateService.ownershipState = StoreOwnershipState.NOT_OWNED;
        final FakeProductActionExecutor actionExecutor = new FakeProductActionExecutor(
            StoreActionExecutionResult.failure("executor failed")
        );
        final StoreServiceImpl service = new StoreServiceImpl(
            this.config(this.repeatableGrant("crate_key", MoneyAmount.ofMinor(750L))),
            economyService,
            runtimeStateService,
            actionExecutor,
            new FakeXpBottleService(XpBottleWithdrawResult.success(100, 0))
        );

        final StorePurchaseResult result = service.purchase(this.player(UUID.randomUUID()), "crate_key");

        assertFalse(result.success());
        assertEquals(1, economyService.withdrawCalls);
        assertEquals(1, economyService.depositCalls);
        assertEquals(1, actionExecutor.calls);
        assertNull(runtimeStateService.lastGrantedEntitlementKey);
        assertEquals(1, runtimeStateService.purchaseRecords.size());
        assertEquals(StorePurchaseStatus.REFUNDED, runtimeStateService.purchaseRecords.getFirst().status());
        assertEquals("executor failed", runtimeStateService.purchaseRecords.getFirst().failureReason());
    }

    @Test
    void purchase_xpWithdrawalBypassesEconomyMutationAndActionExecutor() {
        final FakeEconomyService economyService = new FakeEconomyService(MoneyAmount.ofMinor(5_000L));
        final FakeStoreRuntimeStateService runtimeStateService = new FakeStoreRuntimeStateService();
        final FakeProductActionExecutor actionExecutor = new FakeProductActionExecutor(StoreActionExecutionResult.succeed());
        final FakeXpBottleService xpBottleService = new FakeXpBottleService(XpBottleWithdrawResult.success(100, 250));
        final StoreServiceImpl service = new StoreServiceImpl(
            this.config(this.xpWithdrawal("xp_small", 100)),
            economyService,
            runtimeStateService,
            actionExecutor,
            xpBottleService
        );

        final StorePurchaseResult result = service.purchase(this.player(UUID.randomUUID()), "xp_small");

        assertTrue(result.success());
        assertEquals(0, economyService.withdrawCalls);
        assertEquals(0, economyService.depositCalls);
        assertEquals(0, actionExecutor.calls);
        assertEquals(1, xpBottleService.withdrawCalls);
        assertEquals(1, runtimeStateService.purchaseRecords.size());
        assertEquals(StorePurchaseStatus.SUCCESS, runtimeStateService.purchaseRecords.getFirst().status());
    }

    private StoreProductsConfig config(final StoreProduct product) {
        return new StoreProductsConfig(
            Map.of("store", new StoreCategory("store", "Store", "EMERALD", 0)),
            Map.of(product.productId(), product)
        );
    }

    private StoreProduct permanentUnlock(final String productId, final MoneyAmount price) {
        return new StoreProduct(
            productId,
            "store",
            StoreProductType.PERMANENT_UNLOCK,
            "VIP Rank",
            "EMERALD",
            price,
            "rank.vip",
            false,
            List.of(new StoreAction(StoreActionType.CONSOLE_COMMAND, "lp user %player% parent add vip")),
            0
        );
    }

    private StoreProduct repeatableGrant(final String productId, final MoneyAmount price) {
        return new StoreProduct(
            productId,
            "store",
            StoreProductType.REPEATABLE_GRANT,
            "Crate Key",
            "TRIPWIRE_HOOK",
            price,
            null,
            false,
            List.of(new StoreAction(StoreActionType.CONSOLE_COMMAND, "crate give %player% daily 1")),
            0
        );
    }

    private StoreProduct xpWithdrawal(final String productId, final int xpCostPoints) {
        return new StoreProduct(
            productId,
            "store",
            StoreProductType.XP_WITHDRAWAL,
            "Small XP Bottle",
            "EXPERIENCE_BOTTLE",
            MoneyAmount.zero(),
            null,
            false,
            List.of(),
            xpCostPoints
        );
    }

    private Player player(final UUID playerId) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] { Player.class },
            (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "getName" -> "TestPlayer";
                    case "toString" -> "TestPlayer{" + playerId + "}";
                    case "hashCode" -> playerId.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("Unexpected Player method: " + method.getName());
                };
            }
        );
    }

    private static final class FakeStoreRuntimeStateService implements StoreRuntimeStateService {
        private StoreOwnershipState ownershipState = StoreOwnershipState.NOT_OWNED;
        private int ensureCalls;
        private UUID lastGrantedPlayerId;
        private String lastGrantedEntitlementKey;
        private final List<StorePurchaseAuditRecord> purchaseRecords = new ArrayList<>();

        @Override
        public void ensurePlayerLoadedAsync(final UUID playerId) {
            this.ensureCalls++;
        }

        @Override
        public StoreOwnershipState getOwnershipState(final UUID playerId, final String entitlementKey) {
            return this.ownershipState;
        }

        @Override
        public void grantEntitlement(
            final UUID playerId,
            final String entitlementKey,
            final String productId,
            final long grantedAtEpochSecond
        ) {
            this.lastGrantedPlayerId = playerId;
            this.lastGrantedEntitlementKey = entitlementKey;
            this.ownershipState = StoreOwnershipState.OWNED;
        }

        @Override
        public void recordPurchase(final StorePurchaseAuditRecord record) {
            this.purchaseRecords.add(record);
        }

        @Override
        public void handlePlayerQuit(final UUID playerId) {
        }

        @Override
        public void flushDirtyNow() {
        }

        @Override
        public void shutdown() {
        }
    }

    private static final class FakeProductActionExecutor implements ProductActionExecutor {
        private final StoreActionExecutionResult result;
        private int calls;

        private FakeProductActionExecutor(final StoreActionExecutionResult result) {
            this.result = result;
        }

        @Override
        public StoreActionExecutionResult execute(final Player player, final StoreProduct product) {
            this.calls++;
            return this.result;
        }
    }

    private static final class FakeXpBottleService implements XpBottleService {
        private final XpBottleWithdrawResult result;
        private int withdrawCalls;

        private FakeXpBottleService(final XpBottleWithdrawResult result) {
            this.result = result;
        }

        @Override
        public int getCurrentXpPoints(final Player player) {
            return 0;
        }

        @Override
        public XpBottleWithdrawResult withdrawToBottle(
            final Player player,
            final String productId,
            final String displayName,
            final int xpPoints
        ) {
            this.withdrawCalls++;
            return this.result;
        }

        @Override
        public boolean isCustomBottle(final ItemStack itemStack) {
            return false;
        }

        @Override
        public int getStoredXpPoints(final ItemStack itemStack) {
            return 0;
        }
    }

    private static final class FakeEconomyService implements EconomyService {
        private MoneyAmount balance;
        private int withdrawCalls;
        private int depositCalls;

        private FakeEconomyService(final MoneyAmount initialBalance) {
            this.balance = initialBalance;
        }

        @Override
        public void warmPlayerSession(final UUID playerId, final String playerName) {
        }

        @Override
        public void flushPlayerSession(final UUID playerId, final String playerName) {
        }

        @Override
        public MoneyAmount getBalance(final UUID playerId) {
            return this.balance;
        }

        @Override
        public MoneyAmount getBalanceForSensitiveOperation(final UUID playerId) {
            return this.balance;
        }

        @Override
        public EconomyMutationResult deposit(
            final UUID playerId,
            final MoneyAmount amount,
            final EconomyReason reason,
            final String referenceType,
            final String referenceId
        ) {
            this.depositCalls++;
            this.balance = this.balance.add(amount);
            return EconomyMutationResult.success(this.balance);
        }

        @Override
        public EconomyMutationResult withdraw(
            final UUID playerId,
            final MoneyAmount amount,
            final EconomyReason reason,
            final String referenceType,
            final String referenceId
        ) {
            this.withdrawCalls++;
            if (this.balance.minorUnits() < amount.minorUnits()) {
                return EconomyMutationResult.failure("Insufficient funds", this.balance);
            }
            this.balance = this.balance.subtract(amount);
            return EconomyMutationResult.success(this.balance);
        }

        @Override
        public EconomyMutationResult setBalance(
            final UUID playerId,
            final MoneyAmount balance,
            final EconomyReason reason,
            final String referenceType,
            final String referenceId
        ) {
            this.balance = balance;
            return EconomyMutationResult.success(this.balance);
        }

        @Override
        public EconomyTransferResult transfer(
            final UUID senderId,
            final UUID recipientId,
            final MoneyAmount amount,
            final String referenceType,
            final String referenceId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidate(final UUID playerId) {
        }
    }
}
