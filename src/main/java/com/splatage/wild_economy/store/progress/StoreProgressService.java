package com.splatage.wild_economy.store.progress;

import org.bukkit.entity.Player;

public interface StoreProgressService {
    boolean hasAdvancement(Player player, String advancementKey);
    long getCustomCounter(Player player, String counterKey);
    long incrementCustomCounter(Player player, String counterKey, long delta);
    void setCustomCounter(Player player, String counterKey, long value);
}
