package com.splatage.wild_economy.xp.listener;

import com.splatage.wild_economy.xp.service.XpBottleService;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.inventory.ItemStack;

public final class XpBottleRedeemListener implements Listener {

    private final XpBottleService xpBottleService;

    public XpBottleRedeemListener(final XpBottleService xpBottleService) {
        this.xpBottleService = Objects.requireNonNull(xpBottleService, "xpBottleService");
    }

    @EventHandler(ignoreCancelled = true)
    public void onExpBottle(final ExpBottleEvent event) {
        final ItemStack itemStack = event.getEntity().getItem();
        if (!this.xpBottleService.isCustomBottle(itemStack)) {
            return;
        }

        final int storedXp = this.xpBottleService.getStoredXpPoints(itemStack);
        if (storedXp <= 0) {
            return;
        }

        event.setExperience(storedXp);
    }
}
