package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;

public interface ExchangeBrowseService {
    ExchangeItemView getItemView(ItemKey itemKey);
}
