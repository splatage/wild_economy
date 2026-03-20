package com.splatage.wild_economy.platform;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface PlatformExecutor {

    void runOnPlayer(Player player, Runnable task);

    void runOnLocation(Location location, Runnable task);

    void runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks);

    void cancelPluginTasks();
}
