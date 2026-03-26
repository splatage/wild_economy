package com.splatage.wild_economy.xp.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface XpBottleService {
    int getCurrentXpPoints(Player player);
    XpBottleWithdrawResult withdrawToBottle(Player player, String productId, String displayName, int xpPoints);
    boolean isCustomBottle(ItemStack itemStack);
    int getStoredXpPoints(ItemStack itemStack);
}
