package com.splatage.wild_economy.store.service;

import com.splatage.wild_economy.config.StoreProductsConfig;
import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.persistence.TransactionRunner;
import com.splatage.wild_economy.store.action.ProductActionExecutor;
import com.splatage.wild_economy.store.action.StoreActionExecutionResult;
import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StorePurchaseResult;
import com.splatage.wild_economy.store.model.StorePurchaseStatus;
import com.splatage.wild_economy.store.repository.StoreEntitlementRepository;
import com.splatage.wild_economy.store.repository.StorePurchaseRepository;
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
    private final StoreEntitlementRepository storeEntitlementRepository;
    private final StorePurchaseRepository storePurchaseRepository;
    private final ProductActionExecutor productActionExecutor;
    private final TransactionRunner transactionRunner;
    private final XpBottleService xpBottleService;

    public StoreServiceImpl(
        final StoreProductsConfig storeProductsConfig,
        final EconomyService economyService,
        final StoreEntitlementRepository storeEntitlementRepository,
        final StorePurchaseRepository storePurchaseRepository,
        final ProductActionExecutor productActionExecutor,
        final TransactionRunner transactionRunner,
        final XpBottleService xpBottleService
    ) {
        this.storeProductsConfig = Objects.requireNonNull(storeProductsConfig, "storeProductsConfig");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.storeEntitlementRepository = Objects.requireNonNull(storeEntitlementRepository, "storeEntitlementRepository");
        this.storePurchaseRepository = Objects.requireNonNull(storePurchaseRepository, "storePurchaseRepository");
        this.productActionExecutor = Objects.requireNonNull(productActionExecutor, "productActionExecutor");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner");
        this.xpBottleService = Objects.requireNonNull(xpBottleService, "xpBottleService");
    }

    @Override
    public List<StoreCategory> getCategories() {
        return this.storeProductsConfig.categories().values().stream()
                .sorted(Comparator.comparingInt(StoreCategory::slot))
                .toList();
    }

    @Override
    public List<StoreProduct> getProducts(final String categoryId) {
        return this.storeProductsConfig.products().values().stream()
                .filter(product -> product.categoryId().equals(categoryId))
                .sorted(Comparator.comparing(StoreProduct::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public boolean ownsEntitlement(final UUID playerId, final String entitlementKey) {
        if (entitlementKey == null || entitlementKey.isBlank()) {
            return false;
        }
        return this.storeEntitlementRepository.hasEntitlement(playerId, entitlementKey);
    }

    @Override
    public StorePurchaseResult purchase(final Player player, final String productId) {
        final StoreProduct product = this.storeProductsConfig.product(productId);
        if (product == null) {
            return StorePurchaseResult.failure(
                    "Unknown store product: " + productId,
                    null,
                    this.economyService.getBalance(player.getUniqueId())
            );
        }

        if (product.type() == StoreProductType.PERMANENT_UNLOCK
                && this.ownsEntitlement(player.getUniqueId(), product.entitlementKey())) {
            return StorePurchaseResult.failure(
                    "You already own this unlock.",
                    product,
                    this.economyService.getBalance(player.getUniqueId())
            );
        }

        if (product.type() == StoreProductType.XP_WITHDRAWAL) {
            return this.purchaseXpWithdrawal(player, product);
        }

        final EconomyMutationResult withdrawResult;
        if (product.price().isPositive()) {
            withdrawResult = this.economyService.withdraw(
                    player.getUniqueId(),
                    product.price(),
                    EconomyReason.STORE_PURCHASE,
                    "store",
                    product.productId()
            );
            if (!withdrawResult.success()) {
                this.recordPurchase(player.getUniqueId(), product, StorePurchaseStatus.FAILED, withdrawResult.message());
                return StorePurchaseResult.failure(
                        withdrawResult.message(),
                        product,
                        withdrawResult.resultingBalance()
                );
            }
        } else {
            withdrawResult = EconomyMutationResult.success(this.economyService.getBalance(player.getUniqueId()));
        }

        final StoreActionExecutionResult actionResult = this.productActionExecutor.execute(player, product);
        if (!actionResult.success()) {
            if (product.price().isPositive()) {
                this.economyService.deposit(
                        player.getUniqueId(),
                        product.price(),
                        EconomyReason.STORE_REFUND,
                        "store-refund",
                        product.productId()
                );
            }
            this.recordPurchase(player.getUniqueId(), product, StorePurchaseStatus.REFUNDED, actionResult.message());
            return StorePurchaseResult.failure(
                    actionResult.message(),
                    product,
                    this.economyService.getBalance(player.getUniqueId())
            );
        }

        this.transactionRunner.run(connection -> {
            final long now = Instant.now().getEpochSecond();
            if (product.type() == StoreProductType.PERMANENT_UNLOCK) {
                this.storeEntitlementRepository.upsert(
                        connection,
                        player.getUniqueId(),
                        product.entitlementKey(),
                        product.productId(),
                        now
                );
            }
            this.storePurchaseRepository.insert(
                    connection,
                    player.getUniqueId(),
                    product.productId(),
                    product.type(),
                    product.price(),
                    StorePurchaseStatus.SUCCESS,
                    null,
                    now,
                    now
            );
            return null;
        });

        return StorePurchaseResult.success(product, this.economyService.getBalance(player.getUniqueId()));
    }

    private StorePurchaseResult purchaseXpWithdrawal(final Player player, final StoreProduct product) {
        final XpBottleWithdrawResult xpResult = this.xpBottleService.withdrawToBottle(
                player,
                product.productId(),
                product.displayName(),
                product.xpCostPoints()
        );

        if (!xpResult.success()) {
            this.recordPurchase(player.getUniqueId(), product, StorePurchaseStatus.FAILED, xpResult.message());
            return StorePurchaseResult.failure(
                    xpResult.message(),
                    product,
                    this.economyService.getBalance(player.getUniqueId())
            );
        }

        this.recordPurchase(player.getUniqueId(), product, StorePurchaseStatus.SUCCESS, null);
        return StorePurchaseResult.success(product, this.economyService.getBalance(player.getUniqueId()));
    }

    private void recordPurchase(
        final UUID playerId,
        final StoreProduct product,
        final StorePurchaseStatus status,
        final String failureReason
    ) {
        this.transactionRunner.run(connection -> {
            final long now = Instant.now().getEpochSecond();
            this.storePurchaseRepository.insert(
                    connection,
                    playerId,
                    product.productId(),
                    product.type(),
                    product.price(),
                    status,
                    failureReason,
                    now,
                    now
            );
            return null;
        });
    }
}
