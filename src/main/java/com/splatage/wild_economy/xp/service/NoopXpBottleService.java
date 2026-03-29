package com.splatage.wild_economy.xp.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class NoopXpBottleService implements XpBottleService {

    @Override
    public int getCurrentXpPoints(final Player player) {
        return 0;
    }

    @Override
    public XpBottleWithdrawResult withdrawToBottle(
            final Player player,
            final String productId,
            final String displayName,
            final int xpPoints
    ) {
        return XpBottleWithdrawResult.failure("XP withdrawal is unavailable in the benchmark harness.", 0);
    }

    @Override
    public boolean isCustomBottle(final ItemStack itemStack) {
        return false;
    }

    @Override
    public int getStoredXpPoints(final ItemStack itemStack) {
        return 0;
    }
}
