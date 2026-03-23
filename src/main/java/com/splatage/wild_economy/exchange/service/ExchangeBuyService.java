package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.UUID;

public interface ExchangeBuyService {
    BuyResult buy(UUID playerId, ItemKey itemKey, int amount);

    BuyResult buyQuoted(UUID playerId, ItemKey itemKey, int amount, BigDecimal quotedUnitPrice);
}
