package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ExchangeService {

    SellHandResult sellHand(UUID playerId);

    SellAllResult sellAll(UUID playerId);

    SellContainerResult sellContainer(UUID playerId);

    SellPreviewResult previewInventorySell(UUID playerId);

    BuyResult buy(UUID playerId, ItemKey itemKey, int amount);

    BuyResult buyQuoted(UUID playerId, ItemKey itemKey, int amount, BigDecimal quotedUnitPrice);

    List<ExchangeCatalogView> browseLayout(
        String layoutGroupKey,
        String layoutChildKey,
        int page,
        int pageSize
    );

    int countVisibleItems(String layoutGroupKey, String layoutChildKey);

    List<String> listVisibleChildKeys(String layoutGroupKey);

    ExchangeItemView getItemView(ItemKey itemKey);
}
