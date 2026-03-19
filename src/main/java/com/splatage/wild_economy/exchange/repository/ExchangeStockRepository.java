package com.splatage.wild_economy.exchange.repository;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Map;

public interface ExchangeStockRepository {
    long getStock(ItemKey itemKey);
    Map<ItemKey, Long> getStocks(Iterable<ItemKey> itemKeys);
    void incrementStock(ItemKey itemKey, int amount);
    void decrementStock(ItemKey itemKey, int amount);
    void setStock(ItemKey itemKey, long stock);
}
