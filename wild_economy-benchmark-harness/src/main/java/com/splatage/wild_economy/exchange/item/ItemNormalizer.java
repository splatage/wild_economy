package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public interface ItemNormalizer {
    Optional<ItemKey> normalizeForExchange(ItemStack itemStack);
}
