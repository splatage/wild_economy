package com.splatage.wild_economy.testing.scenario.impl;

import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.exchange.activity.MarketActivityCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StorePurchaseStatus;
import com.splatage.wild_economy.store.state.StoreOwnershipState;
import com.splatage.wild_economy.store.state.StorePurchaseAuditRecord;
import com.splatage.wild_economy.testing.scenario.Scenario;
import com.splatage.wild_economy.testing.scenario.ScenarioContext;
import com.splatage.wild_economy.testing.scenario.ScenarioExecutionResult;
import com.splatage.wild_economy.testing.scenario.ScenarioSelection;

public final class MixedEconomyScenario implements Scenario {

    private final BrowseHeavyScenario browseHeavyScenario = new BrowseHeavyScenario();
    private final BuyHeavyScenario buyHeavyScenario = new BuyHeavyScenario();
    private final SellHeavyScenario sellHeavyScenario = new SellHeavyScenario();

    @Override
    public String name() {
        return "mixed-economy";
    }

    @Override
    public ScenarioExecutionResult execute(final ScenarioContext context, final ScenarioSelection selection) {
        final ScenarioExecutionResult browseResult = this.browseHeavyScenario.execute(context, selection);
        if (!browseResult.success()) {
            return browseResult;
        }

        if ((selection.player().playerId().getLeastSignificantBits() & 1L) == 0L) {
            final ScenarioExecutionResult buyResult = this.buyHeavyScenario.execute(context, selection);
            if (!buyResult.success()) {
                return buyResult;
            }
        } else {
            final ScenarioExecutionResult sellResult = this.sellHeavyScenario.execute(context, selection);
            if (!sellResult.success()) {
                return sellResult;
            }
        }

        final StoreProduct product = selection.storeProduct();
        if (product != null) {
            if (product.entitlementKey() != null && !product.entitlementKey().isBlank()) {
                context.components().storeRuntimeStateService().ensurePlayerLoadedAsync(selection.player().playerId());
                final StoreOwnershipState ownershipState = context.components().storeRuntimeStateService().getOwnershipState(
                        selection.player().playerId(),
                        product.entitlementKey()
                );
                if (ownershipState == StoreOwnershipState.NOT_OWNED) {
                    context.components().storeRuntimeStateService().grantEntitlement(
                            selection.player().playerId(),
                            product.entitlementKey(),
                            product.productId(),
                            context.seedPlan().nowEpochSecond()
                    );
                }
            }
            context.components().storeRuntimeStateService().recordPurchase(new StorePurchaseAuditRecord(
                    selection.player().playerId(),
                    product.productId(),
                    product.type(),
                    product.price(),
                    StorePurchaseStatus.SUCCESS,
                    null,
                    context.seedPlan().nowEpochSecond(),
                    context.seedPlan().nowEpochSecond()
            ));
        }

        context.components().economyService().getBalanceForSensitiveOperation(selection.player().playerId());
        context.components().marketActivityService().listItems(
                MarketActivityCategory.TOP_TURNOVER,
                null,
                8
        );
        context.components().economyService().deposit(
                selection.player().playerId(),
                MoneyAmount.ofMinor(1L),
                EconomyReason.ADMIN_GIVE,
                "HARNESS_SCENARIO",
                this.name() + "-rebate"
        );
        return ScenarioExecutionResult.succeeded();
    }
}
