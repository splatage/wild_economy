package com.splatage.wild_economy.testing.scenario.impl;

import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.testing.scenario.Scenario;
import com.splatage.wild_economy.testing.scenario.ScenarioContext;
import com.splatage.wild_economy.testing.scenario.ScenarioExecutionResult;
import com.splatage.wild_economy.testing.scenario.ScenarioSelection;

public final class BuyHeavyScenario implements Scenario {

    @Override
    public String name() {
        return "buy-heavy";
    }

    @Override
    public ScenarioExecutionResult execute(final ScenarioContext context, final ScenarioSelection selection) {
        final ExchangeCatalogEntry entry = selection.exchangeEntry();
        final StockSnapshot snapshot = context.components().stockService().getSnapshot(entry.itemKey());
        final int amount = Math.max(1, selection.amount());
        final BuyQuote quote = context.components().pricingService().quoteBuy(entry.itemKey(), amount, snapshot);
        final MoneyAmount totalPrice = MoneyAmount.fromMajor(quote.totalPrice(), context.components().economyConfig().fractionalDigits());
        final boolean playerStocked = entry.policyMode() == ItemPolicyMode.PLAYER_STOCKED;

        if (playerStocked && snapshot.stockCount() < amount) {
            return ScenarioExecutionResult.failed("Out of stock for " + entry.itemKey().value());
        }

        if (playerStocked && !context.components().stockService().tryConsume(entry.itemKey(), amount)) {
            return ScenarioExecutionResult.failed("Concurrent stock miss for " + entry.itemKey().value());
        }

        final EconomyMutationResult withdrawal = context.components().economyService().withdraw(
                selection.player().playerId(),
                totalPrice,
                EconomyReason.EXCHANGE_BUY,
                "HARNESS_SCENARIO",
                this.name()
        );
        if (!withdrawal.success()) {
            if (playerStocked) {
                context.components().stockService().addStock(entry.itemKey(), amount);
            }
            return ScenarioExecutionResult.failed(withdrawal.message());
        }

        context.components().transactionLogService().logPurchase(
                selection.player().playerId(),
                entry.itemKey(),
                amount,
                quote.unitPrice(),
                quote.totalPrice()
        );
        return ScenarioExecutionResult.succeeded();
    }
}
