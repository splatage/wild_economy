package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import org.bukkit.inventory.ItemStack;

public final class CanonicalItemRules {

    private final ExchangeItemCodec exchangeItemCodec = new ExchangeItemCodec();

    public boolean isCanonicalForExchange(final ItemStack itemStack) {
        return this.exchangeItemCodec.itemKeyForExchange(itemStack).isPresent();
    }

    public ItemKey toItemKey(final ItemStack itemStack) {
        return this.exchangeItemCodec.itemKeyForExchange(itemStack)
            .orElseThrow(() -> new IllegalArgumentException("Item is not canonical for Exchange"));
    }
}
