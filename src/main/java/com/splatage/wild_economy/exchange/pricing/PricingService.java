package com.splatage.wild_economy.exchange.pricing;

import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public interface PricingService {
    BuyQuote quoteBuy(ItemKey itemKey, int amount, StockSnapshot stockSnapshot);
    SellQuote quoteSell(ItemKey itemKey, int amount, StockSnapshot stockSnapshot);
}
