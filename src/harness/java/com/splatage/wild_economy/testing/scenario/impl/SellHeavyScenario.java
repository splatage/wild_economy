package com.splatage.wild_economy.testing.scenario.impl;

import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.testing.scenario.Scenario;
import com.splatage.wild_economy.testing.scenario.ScenarioContext;
import com.splatage.wild_economy.testing.scenario.ScenarioExecutionResult;
import com.splatage.wild_economy.testing.scenario.ScenarioSelection;

public final class SellHeavyScenario implements Scenario {

    @Override
    public String name() {
        return "sell-heavy";
    }

    @Override
    public ScenarioExecutionResult execute(final ScenarioContext context, final ScenarioSelection selection) {
        final ExchangeCatalogEntry entry = selection.exchangeEntry();
        final StockSnapshot snapshot = context.components().stockService().getSnapshot(entry.itemKey());
        final int amount = Math.max(1, selection.amount());
        final SellQuote quote = context.components().pricingService().quoteSell(entry.itemKey(), amount, snapshot);
        final MoneyAmount totalPrice = MoneyAmount.fromMajor(quote.totalPrice(), context.components().economyConfig().fractionalDigits());
        if (totalPrice.isZero()) {
            return ScenarioExecutionResult.failed("Zero-value sell quote for " + entry.itemKey().value());
        }

        final EconomyMutationResult payout = context.components().economyService().deposit(
                selection.player().playerId(),
                totalPrice,
                EconomyReason.EXCHANGE_SELL,
                "HARNESS_SCENARIO",
                this.name()
        );
        if (!payout.success()) {
            return ScenarioExecutionResult.failed(payout.message());
        }

        context.components().stockService().addStock(entry.itemKey(), amount);
        context.components().transactionLogService().logSale(
                selection.player().playerId(),
                entry.itemKey(),
                amount,
                quote.effectiveUnitPrice(),
                quote.totalPrice()
        );
        context.components().supplierStatsService().recordSale(selection.player().playerId(), entry.itemKey(), amount);
        return ScenarioExecutionResult.succeeded();
    }
}
