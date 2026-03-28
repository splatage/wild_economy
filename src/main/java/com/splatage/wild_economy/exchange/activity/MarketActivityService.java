package com.splatage.wild_economy.exchange.activity;

import java.util.List;
import java.util.UUID;

public interface MarketActivityService {

    List<MarketActivityItemView> listItems(MarketActivityCategory category, UUID playerId, int limit);
}
