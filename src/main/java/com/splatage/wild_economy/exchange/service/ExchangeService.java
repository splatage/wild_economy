package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import java.math.BigDecimal;
import java.util.List;
import org.bukkit.entity.Player;

public interface ExchangeService {

    SellHandResult sellHand(Player player);

    SellAllResult sellAll(Player player);

    SellContainerResult sellContainer(Player player);

    SellPreviewResult previewInventorySell(Player player);

    SellPreviewResult previewContainerSell(Player player);

    BuyResult buy(Player player, ItemKey itemKey, int amount);

    BuyResult buyQuoted(Player player, ItemKey itemKey, int amount, BigDecimal quotedUnitPrice);

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
