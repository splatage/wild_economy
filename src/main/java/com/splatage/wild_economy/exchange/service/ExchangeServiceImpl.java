package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.List;
import java.util.UUID;

public final class ExchangeServiceImpl implements ExchangeService {

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(final ItemCategory category, final int page, final int pageSize) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
