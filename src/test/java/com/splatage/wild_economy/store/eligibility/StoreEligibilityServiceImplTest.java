package com.splatage.wild_economy.store.eligibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.store.model.StoreAction;
import com.splatage.wild_economy.store.model.StoreActionType;
import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StoreRequirement;
import com.splatage.wild_economy.store.model.StoreRequirementType;
import com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet;
import com.splatage.wild_economy.store.state.StoreEntitlementRecord;
import com.splatage.wild_economy.store.state.StoreOwnershipState;
import com.splatage.wild_economy.store.state.StorePurchaseAuditRecord;
import com.splatage.wild_economy.store.state.StoreRuntimeStateService;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

final class StoreEligibilityServiceImplTest {

    @Test
    void category_canBeHiddenByPermissionRequirement() {
        final StoreEligibilityService service = this.service(new FakeStoreRuntimeStateService(), 300L);
        final Player player = this.player(UUID.randomUUID(), Set.of(), Map.of(), Map.of());
        final StoreCategory category = new StoreCategory(
            "vip",
            "VIP",
            "NETHER_STAR",
            11,
            List.of(new StoreRequirement(StoreRequirementType.PERMISSION, null, "wild.store.vip", null, null, 0L)),
            StoreVisibilityWhenUnmet.HIDE,
            "Support the server to unlock this section."
        );

        final StoreEligibilityResult result = service.evaluateCategory(player, category);

        assertFalse(result.visible());
        assertFalse(result.acquirable());
        assertNull(result.blockedMessage());
    }

    @Test
    void product_showsLockedWhenStatisticRequirementIsUnmet() {
        final StoreEligibilityService service = this.service(new FakeStoreRuntimeStateService(), 300L);
        final Player player = this.player(
            UUID.randomUUID(),
            Set.of(),
            Map.of(Statistic.PLAY_ONE_MINUTE, 20 * 60 * 60),
            Map.of()
        );
        final StoreProduct product = new StoreProduct(
            "track.2",
            "tracks",
            StoreProductType.PERMANENT_UNLOCK,
            "Track II",
            "BOOK",
            MoneyAmount.ofMinor(1_000L),
            "track.2",
            true,
            List.of(),
            List.of(new StoreAction(StoreActionType.MESSAGE, "hi")),
            0,
            List.of(new StoreRequirement(StoreRequirementType.STATISTIC, null, null, Statistic.PLAY_ONE_MINUTE.name(), null, 20L * 60L * 60L * 10L)),
            StoreVisibilityWhenUnmet.SHOW_LOCKED,
            "Keep progressing."
        );

        final StoreEligibilityResult result = service.evaluateProduct(player, product);

        assertTrue(result.visible());
        assertFalse(result.acquirable());
        assertTrue(result.progressLines().stream().anyMatch(line -> line.contains("play one minute")));
    }

    @Test
    void product_requiresPreviousTierInSequence() {
        final FakeStoreRuntimeStateService runtime = new FakeStoreRuntimeStateService();
        final StoreEligibilityService service = this.service(runtime, 300L);
        final UUID playerId = UUID.randomUUID();
        final Player player = this.player(playerId, Set.of(), Map.of(), Map.of());

        final StoreEligibilityResult result = service.evaluateProduct(player, this.tieredProduct("kit.2"));

        assertTrue(result.visible());
        assertFalse(result.acquirable());
        assertTrue(result.progressLines().stream().anyMatch(line -> line.contains("kit.1")));
    }

    @Test
    void product_appliesTrackCooldownFromPreviousGrant() {
        final FakeStoreRuntimeStateService runtime = new FakeStoreRuntimeStateService();
        final long now = System.currentTimeMillis() / 1000L;
        runtime.entitlements.put("kit.1", new StoreEntitlementRecord("kit.1", "kit_1", now - 120L));
        final StoreEligibilityService service = this.service(runtime, 300L);
        final Player player = this.player(UUID.randomUUID(), Set.of(), Map.of(), Map.of());

        final StoreEligibilityResult result = service.evaluateProduct(player, this.tieredProduct("kit.2"));

        assertFalse(result.acquirable());
        assertTrue(result.progressLines().stream().anyMatch(line -> line.toLowerCase().contains("cooldown")));
    }

    @Test
    void product_permissionRequirementPassesForAuthorizedPlayer() {
        final StoreEligibilityService service = this.service(new FakeStoreRuntimeStateService(), 0L);
        final Player player = this.player(UUID.randomUUID(), Set.of("wild.store.vip"), Map.of(), Map.of());
        final StoreProduct product = new StoreProduct(
            "vip_rank",
            "tracks",
            StoreProductType.REPEATABLE_GRANT,
            "VIP Rank",
            "EMERALD",
            MoneyAmount.ofMinor(1_000L),
            null,
            false,
            List.of(),
            List.of(new StoreAction(StoreActionType.MESSAGE, "hi")),
            0,
            List.of(new StoreRequirement(StoreRequirementType.PERMISSION, null, "wild.store.vip", null, null, 0L)),
            StoreVisibilityWhenUnmet.SHOW_LOCKED,
            null
        );

        final StoreEligibilityResult result = service.evaluateProduct(player, product);

        assertTrue(result.visible());
        assertTrue(result.acquirable());
    }

    private StoreEligibilityService service(final FakeStoreRuntimeStateService runtime, final long cooldownSeconds) {
        return new StoreEligibilityServiceImpl(runtime, cooldownSeconds);
    }

    private StoreProduct tieredProduct(final String entitlementKey) {
        return new StoreProduct(
            entitlementKey,
            "tracks",
            StoreProductType.PERMANENT_UNLOCK,
            entitlementKey,
            "BOOK",
            MoneyAmount.ofMinor(1_000L),
            entitlementKey,
            true,
            List.of(),
            List.of(new StoreAction(StoreActionType.MESSAGE, "hi")),
            0,
            List.of(),
            StoreVisibilityWhenUnmet.SHOW_LOCKED,
            "Keep progressing."
        );
    }

    private Player player(
        final UUID playerId,
        final Set<String> permissions,
        final Map<Statistic, Integer> simpleStats,
        final Map<String, Integer> materialStats
    ) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] { Player.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "hasPermission" -> permissions.contains(String.valueOf(args[0]));
                case "getStatistic" -> {
                    if (args.length == 1) {
                        yield simpleStats.getOrDefault((Statistic) args[0], 0);
                    }
                    if (args.length == 2 && args[1] instanceof Material material) {
                        final Statistic statistic = (Statistic) args[0];
                        yield materialStats.getOrDefault(statistic.name() + ":" + material.name(), 0);
                    }
                    throw new UnsupportedOperationException();
                }
                case "getName" -> "TestPlayer";
                case "toString" -> "TestPlayer{" + playerId + "}";
                case "hashCode" -> playerId.hashCode();
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Unexpected Player method: " + method.getName());
            }
        );
    }

    private static final class FakeStoreRuntimeStateService implements StoreRuntimeStateService {
        private final Map<String, StoreOwnershipState> ownershipByEntitlement = new HashMap<>();
        private final Map<String, StoreEntitlementRecord> entitlements = new HashMap<>();

        @Override
        public void ensurePlayerLoadedAsync(final UUID playerId) {
        }

        @Override
        public StoreOwnershipState getOwnershipState(final UUID playerId, final String entitlementKey) {
            final StoreOwnershipState explicit = this.ownershipByEntitlement.get(entitlementKey);
            if (explicit != null) {
                return explicit;
            }
            return this.entitlements.containsKey(entitlementKey) ? StoreOwnershipState.OWNED : StoreOwnershipState.NOT_OWNED;
        }

        @Override
        public StoreEntitlementRecord getEntitlementRecord(final UUID playerId, final String entitlementKey) {
            return this.entitlements.get(entitlementKey);
        }

        @Override
        public void grantEntitlement(
            final UUID playerId,
            final String entitlementKey,
            final String productId,
            final long grantedAtEpochSecond
        ) {
        }

        @Override
        public void recordPurchase(final StorePurchaseAuditRecord record) {
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
}
