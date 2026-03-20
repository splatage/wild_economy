package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public interface StockService {

    StockSnapshot getSnapshot(ItemKey itemKey);

    long getAvailableRoom(ItemKey itemKey);

    void addStock(ItemKey itemKey, int amount);

    void removeStock(ItemKey itemKey, int amount);

    void shutdown();
}
