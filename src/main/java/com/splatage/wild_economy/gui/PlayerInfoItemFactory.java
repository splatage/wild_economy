package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.EconomyFormatter;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.xp.service.XpBottleService;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class PlayerInfoItemFactory {

    private final PlayerHeadCache playerHeadCache;
    private final EconomyService economyService;
    private final XpBottleService xpBottleService;
    private final EconomyConfig economyConfig;

    public PlayerInfoItemFactory(
        final PlayerHeadCache playerHeadCache,
        final EconomyService economyService,
        final XpBottleService xpBottleService,
        final EconomyConfig economyConfig
    ) {
        this.playerHeadCache = Objects.requireNonNull(playerHeadCache, "playerHeadCache");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.xpBottleService = Objects.requireNonNull(xpBottleService, "xpBottleService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    public ItemStack create(final Player player) {
        final ItemStack head = this.playerHeadCache.getBaseHead(player).clone();
        final ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(player.getName());
            meta.setLore(List.of(
                    "Balance: " + EconomyFormatter.format(
                            this.economyService.getBalance(player.getUniqueId()),
                            this.economyConfig
                    ),
                    "XP: " + this.xpBottleService.getCurrentXpPoints(player)
            ));
            head.setItemMeta(meta);
        }
        return head;
    }
}
