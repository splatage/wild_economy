package com.splatage.wild_economy.exchange.repository;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Map;

public interface ExchangeStockRepository {

    long getStock(ItemKey itemKey);

    Map<ItemKey, Long> getStocks(Iterable<ItemKey> itemKeys);

    Map<ItemKey, Long> loadAllStocks();

    void incrementStock(ItemKey itemKey, int amount);

    void decrementStock(ItemKey itemKey, int amount);

    void setStock(ItemKey itemKey, long stock);

    void flushStocks(Map<ItemKey, Long> stockByItemKey);
}
