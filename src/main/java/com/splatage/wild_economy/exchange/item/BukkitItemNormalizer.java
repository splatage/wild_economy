package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class BukkitItemNormalizer implements ItemNormalizer {

    @Override
    public Optional<ItemKey> normalizeForExchange(final ItemStack itemStack) {
        return Optional.empty();
    }
}
