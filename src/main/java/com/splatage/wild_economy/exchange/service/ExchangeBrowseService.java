package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.List;

public interface ExchangeBrowseService {
    List<ExchangeCatalogView> browseCategory(ItemCategory category, GeneratedItemCategory generatedCategory, int page, int pageSize);

    int countVisibleItems(ItemCategory category, GeneratedItemCategory generatedCategory);

    List<GeneratedItemCategory> listVisibleSubcategories(ItemCategory category);

    ExchangeItemView getItemView(ItemKey itemKey);
}
