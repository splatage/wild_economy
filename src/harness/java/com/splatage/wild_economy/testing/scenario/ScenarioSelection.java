package com.splatage.wild_economy.testing.scenario;

import com.splatage.wild_economy.exchange.activity.MarketActivityCategory;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.testing.support.SeedPlayer;

public record ScenarioSelection(
        SeedPlayer player,
        ExchangeCatalogEntry exchangeEntry,
        StoreProduct storeProduct,
        MarketActivityCategory marketActivityCategory,
        int amount
) {
}
