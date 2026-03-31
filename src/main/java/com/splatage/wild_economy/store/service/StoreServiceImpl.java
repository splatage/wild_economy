package com.splatage.wild_economy.store.service;

import com.splatage.wild_economy.config.StoreProductsConfig;
import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.store.action.ProductActionExecutor;
import com.splatage.wild_economy.store.action.StoreActionExecutionResult;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityService;
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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class StoreServiceImpl implements StoreService {

    private final StoreProductsConfig storeProductsConfig;
    private final EconomyService economyService;
    private final StoreRuntimeStateService storeRuntimeStateService;
    private final ProductActionExecutor productActionExecutor;
    private final XpBottleService xpBottleService;
    private final StoreEligibilityService storeEligibilityService;

    public StoreServiceImpl(
        final StoreProductsConfig storeProductsConfig,
        final EconomyService economyService,
        final StoreRuntimeStateService storeRuntimeStateService,
        final ProductActionExecutor productActionExecutor,
        final XpBottleService xpBottleService,
        final StoreEligibilityService storeEligibilityService
    ) {
        this.storeProductsConfig = Objects.requireNonNull(storeProductsConfig, "storeProductsConfig");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.storeRuntimeStateService = Objects.requireNonNull(storeRuntimeStateService, "storeRuntimeStateService");
        this.productActionExecutor = Objects.requireNonNull(productActionExecutor, "productActionExecutor");
        this.xpBottleService = Objects.requireNonNull(xpBottleService, "xpBottleService");
        this.storeEligibilityService = Objects.requireNonNull(storeEligibilityService, "storeEligibilityService");
    }

    @Override
    public List<StoreCategory> getCategories() {
        return this.storeProductsConfig.categories().values().stream()
                .sorted(Comparator.comparingInt(StoreCategory::slot))
                .toList();
    }

    @Override
    public List<StoreCategory> getVisibleCategories(final Player player) {
        return this.getCategories().stream()
                .filter(category -> this.storeEligibilityService.evaluateCategory(player, category).visible())
                .toList();
    }

    @Override
    public List<StoreProduct> getProducts(final String categoryId) {
        return this.storeProductsConfig.products().values().stream()
                .filter(product -> product.categoryId().equals(categoryId))
                .sorted(Comparator.comparing(StoreProduct::slot, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    @Override
    public List<StoreProduct> getVisibleProducts(final Player player, final String categoryId) {
        return this.getProducts(categoryId).stream()
                .filter(product -> this.storeEligibilityService.evaluateProduct(player, product).visible())
                .toList();
    }

    @Override
    public StoreEligibilityResult getCategoryEligibility(final Player player, final String categoryId) {
        final StoreCategory category = this.storeProductsConfig.category(categoryId);
        if (category == null) {
            return StoreEligibilityResult.hidden();
        }
        return this.storeEligibilityService.evaluateCategory(player, category);
    }

    @Override
    public StoreEligibilityResult getProductEligibility(final Player player, final String productId) {
        final StoreProduct product = this.storeProductsConfig.product(productId);
        if (product == null) {
            return StoreEligibilityResult.hidden();
        }
        return this.storeEligibilityService.evaluateProduct(player, product);
    }

    @Override
    public void ensurePlayerLoadedAsync(final UUID playerId) {
        this.storeRuntimeStateService.ensurePlayerLoadedAsync(playerId);
    }

    @Override
    public StoreOwnershipState getOwnershipState(final UUID playerId, final String entitlementKey) {
        return this.storeRuntimeStateService.getOwnershipState(playerId, entitlementKey);
    }

    @Override
    public StorePurchaseResult purchase(final Player player, final String productId) {
        final UUID playerId = player.getUniqueId();
        final StoreProduct product = this.storeProductsConfig.product(productId);
        if (product == null) {
            return StorePurchaseResult.failure(
                    "Unknown store product: " + productId,
                    null,
                    this.economyService.getBalance(playerId)
            );
        }

        final StoreEligibilityResult eligibility = this.storeEligibilityService.evaluateProduct(player, product);
        if (!eligibility.visible() || !eligibility.acquirable()) {
            return StorePurchaseResult.failure(
                    eligibility.blockedMessage() == null ? "This product is currently unavailable." : eligibility.blockedMessage(),
                    product,
                    this.economyService.getBalance(playerId)
            );
        }

        if (product.type() == StoreProductType.XP_WITHDRAWAL) {
            return this.purchaseXpWithdrawal(player, product);
        }

        final EconomyMutationResult withdrawResult;
        if (product.price().isPositive()) {
            withdrawResult = this.economyService.withdraw(
                    playerId,
                    product.price(),
                    EconomyReason.STORE_PURCHASE,
                    "store",
                    product.productId()
            );
            if (!withdrawResult.success()) {
                this.recordPurchase(playerId, product, StorePurchaseStatus.FAILED, withdrawResult.message());
                return StorePurchaseResult.failure(
                        withdrawResult.message(),
                        product,
                        withdrawResult.resultingBalance()
                );
            }
        } else {
            withdrawResult = EconomyMutationResult.success(this.economyService.getBalance(playerId));
        }

        final StoreActionExecutionResult actionResult = this.productActionExecutor.execute(player, product);
        if (!actionResult.success()) {
            final MoneyAmount refundedBalance;
            if (product.price().isPositive()) {
                refundedBalance = this.economyService.deposit(
                        playerId,
                        product.price(),
                        EconomyReason.STORE_REFUND,
                        "store-refund",
                        product.productId()
                ).resultingBalance();
            } else {
                refundedBalance = withdrawResult.resultingBalance();
            }
            this.recordPurchase(playerId, product, StorePurchaseStatus.REFUNDED, actionResult.message());
            return StorePurchaseResult.failure(
                    actionResult.message(),
                    product,
                    refundedBalance
            );
        }

        final long now = Instant.now().getEpochSecond();
        if (product.type() == StoreProductType.PERMANENT_UNLOCK) {
            this.storeRuntimeStateService.grantEntitlement(
                    playerId,
                    product.entitlementKey(),
                    product.productId(),
                    now
            );
        }
        this.recordPurchase(playerId, product, StorePurchaseStatus.SUCCESS, null, now, now);

        return StorePurchaseResult.success(product, withdrawResult.resultingBalance());
    }

    private StorePurchaseResult purchaseXpWithdrawal(final Player player, final StoreProduct product) {
        final UUID playerId = player.getUniqueId();
        final XpBottleWithdrawResult xpResult = this.xpBottleService.withdrawToBottle(
                player,
                product.productId(),
                product.displayName(),
                product.xpCostPoints()
        );

        if (!xpResult.success()) {
            this.recordPurchase(playerId, product, StorePurchaseStatus.FAILED, xpResult.message());
            return StorePurchaseResult.failure(
                    xpResult.message(),
                    product,
                    this.economyService.getBalance(playerId)
            );
        }

        this.recordPurchase(playerId, product, StorePurchaseStatus.SUCCESS, null);
        return StorePurchaseResult.success(product, this.economyService.getBalance(playerId));
    }

    private void recordPurchase(
        final UUID playerId,
        final StoreProduct product,
        final StorePurchaseStatus status,
        final String failureReason
    ) {
        final long now = Instant.now().getEpochSecond();
        this.recordPurchase(playerId, product, status, failureReason, now, now);
    }

    private void recordPurchase(
        final UUID playerId,
        final StoreProduct product,
        final StorePurchaseStatus status,
        final String failureReason,
        final long createdAtEpochSecond,
        final Long completedAtEpochSecond
    ) {
        this.storeRuntimeStateService.recordPurchase(new StorePurchaseAuditRecord(
                playerId,
                product.productId(),
                product.type(),
                product.price(),
                status,
                failureReason,
                createdAtEpochSecond,
                completedAtEpochSecond
        ));
    }
}
