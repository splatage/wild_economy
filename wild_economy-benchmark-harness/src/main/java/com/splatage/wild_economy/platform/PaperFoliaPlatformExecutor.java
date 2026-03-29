package com.splatage.wild_economy.platform;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PaperFoliaPlatformExecutor implements PlatformExecutor {

    private final Plugin plugin;

    public PaperFoliaPlatformExecutor(final Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void runOnPlayer(final Player player, final Runnable task) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
            return;
        }

        player.getScheduler().run(this.plugin, scheduledTask -> task.run(), null);
    }

    @Override
    public void runOnLocation(final Location location, final Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");

        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location.world");
        }

        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
            return;
        }

        this.plugin.getServer().getRegionScheduler().execute(this.plugin, location, task);
    }

    @Override
    public void runGlobalRepeating(final Runnable task, final long initialDelayTicks, final long periodTicks) {
        Objects.requireNonNull(task, "task");

        this.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
            this.plugin,
            scheduledTask -> task.run(),
            initialDelayTicks,
            periodTicks
        );
    }

    @Override
    public void cancelPluginTasks() {
        this.plugin.getServer().getGlobalRegionScheduler().cancelTasks(this.plugin);
    }
}
