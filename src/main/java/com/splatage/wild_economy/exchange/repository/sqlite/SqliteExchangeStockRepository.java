package com.splatage.wild_economy.exchange.repository.sqlite;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import java.util.Map;

public final class SqliteExchangeStockRepository implements ExchangeStockRepository {

    @Override
    public long getStock(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Map<ItemKey, Long> getStocks(final Iterable<ItemKey> itemKeys) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void incrementStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void decrementStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setStock(final ItemKey itemKey, final long stock) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
