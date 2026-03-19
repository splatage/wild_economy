package com.splatage.wild_economy.exchange.domain;

import org.bukkit.inventory.ItemStack;

public record PlannedSale(
    int slot,
    ItemStack originalStack,
    ItemKey itemKey,
    String displayName,
    int amount,
    SellQuote quote
) {}
