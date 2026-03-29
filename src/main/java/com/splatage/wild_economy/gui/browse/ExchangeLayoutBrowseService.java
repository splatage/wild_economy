package com.splatage.wild_economy.gui.browse;

import com.splatage.wild_economy.exchange.service.ExchangeCatalogView;
import java.util.List;
import java.util.UUID;

public interface ExchangeLayoutBrowseService {
    List<ExchangeCatalogView> browseLayout(String layoutGroupKey, String layoutChildKey, int page, int pageSize);

    int countVisibleItems(String layoutGroupKey, String layoutChildKey);

    List<String> listVisibleChildKeys(String layoutGroupKey);

    void handleExchangeViewOpened(UUID playerId);

    void handleExchangeViewClosed(UUID playerId);

    void shutdown();
}
