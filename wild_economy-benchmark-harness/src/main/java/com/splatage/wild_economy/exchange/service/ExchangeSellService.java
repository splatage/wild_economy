package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import org.bukkit.entity.Player;

public interface ExchangeSellService {

    SellHandResult sellHand(Player player);

    SellAllResult sellAll(Player player);

    SellContainerResult sellContainer(Player player);

    SellPreviewResult previewInventorySell(Player player);

    SellPreviewResult previewContainerSell(Player player);
}
