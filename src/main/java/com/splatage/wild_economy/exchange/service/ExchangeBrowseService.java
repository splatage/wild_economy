package com.splatage.wild_economy.exchange.service;

public interface ExchangeBrowseService {
}
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.List;

public interface ExchangeBrowseService {
    List<ExchangeCatalogView> browseCategory(ItemCategory category, int page, int pageSize);
    ExchangeItemView getItemView(ItemKey itemKey);
}
