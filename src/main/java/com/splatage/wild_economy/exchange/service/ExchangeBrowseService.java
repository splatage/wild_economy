package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.List;

public interface ExchangeBrowseService {
    List<ExchangeCatalogView> browseLayout(String layoutGroupKey, String layoutChildKey, int page, int pageSize);

    int countVisibleItems(String layoutGroupKey, String layoutChildKey);

    List<String> listVisibleChildKeys(String layoutGroupKey);

    ExchangeItemView getItemView(ItemKey itemKey);
}
