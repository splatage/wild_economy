package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public final class StockServiceImpl implements StockService {

    @Override
    public StockSnapshot getSnapshot(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public long getAvailableRoom(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeStock(final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
