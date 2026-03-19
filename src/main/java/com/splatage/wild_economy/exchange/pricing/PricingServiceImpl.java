package com.splatage.wild_economy.exchange.pricing;

import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public final class PricingServiceImpl implements PricingService {

    @Override
    public BuyQuote quoteBuy(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SellQuote quoteSell(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
