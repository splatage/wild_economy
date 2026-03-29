package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import org.bukkit.entity.Player;

public interface ExchangeBuyService {
    BuyResult buy(Player player, ItemKey itemKey, int amount);

    BuyResult buyQuoted(Player player, ItemKey itemKey, int amount, BigDecimal quotedUnitPrice);
}
