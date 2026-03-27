package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import java.util.UUID;

public interface ExchangeSellService {

    SellHandResult sellHand(UUID playerId);

    SellAllResult sellAll(UUID playerId);

    SellContainerResult sellContainer(UUID playerId);

    SellPreviewResult previewInventorySell(UUID playerId);
}
