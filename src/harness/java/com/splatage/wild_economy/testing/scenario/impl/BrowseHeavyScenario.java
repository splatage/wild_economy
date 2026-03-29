package com.splatage.wild_economy.testing.scenario.impl;

import com.splatage.wild_economy.exchange.activity.MarketActivityCategory;
import com.splatage.wild_economy.testing.scenario.Scenario;
import com.splatage.wild_economy.testing.scenario.ScenarioContext;
import com.splatage.wild_economy.testing.scenario.ScenarioExecutionResult;
import com.splatage.wild_economy.testing.scenario.ScenarioSelection;

public final class BrowseHeavyScenario implements Scenario {

    @Override
    public String name() {
        return "browse-heavy";
    }

    @Override
    public ScenarioExecutionResult execute(final ScenarioContext context, final ScenarioSelection selection) {
        final var itemView = context.components().exchangeBrowseService().getItemView(selection.exchangeEntry().itemKey());
        if (!itemView.itemKey().equals(selection.exchangeEntry().itemKey())) {
            return ScenarioExecutionResult.failed("Browse returned mismatched item view for " + selection.exchangeEntry().itemKey().value());
        }
        context.components().pricingService().quoteBuy(selection.exchangeEntry().itemKey(), 1, context.components().stockService().getSnapshot(selection.exchangeEntry().itemKey()));
        final MarketActivityCategory category = selection.marketActivityCategory();
        context.components().marketActivityService().listItems(
                category,
                category == MarketActivityCategory.YOUR_RECENT_PURCHASES ? selection.player().playerId() : null,
                12
        );
        return ScenarioExecutionResult.succeeded();
    }
}
