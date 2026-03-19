package com.splatage.wild_economy.exchange.service;

public interface ExchangeBuyService {
}
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.UUID;

public interface ExchangeBuyService {
    BuyResult buy(UUID playerId, ItemKey itemKey, int amount);
}
