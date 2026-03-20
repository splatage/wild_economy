package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.List;
import java.util.UUID;

public interface ExchangeService {

    SellHandResult sellHand(UUID playerId);

    SellAllResult sellAll(UUID playerId);

    SellContainerResult sellContainer(UUID playerId);

    BuyResult buy(UUID playerId, ItemKey itemKey, int amount);

    List<ExchangeCatalogView> browseCategory(
        ItemCategory category,
        GeneratedItemCategory generatedCategory,
        int page,
        int pageSize
    );

    int countVisibleItems(ItemCategory category, GeneratedItemCategory generatedCategory);

    List<GeneratedItemCategory> listVisibleSubcategories(ItemCategory category);

    ExchangeItemView getItemView(ItemKey itemKey);
}
