package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.WildEconomyPlugin;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class PlayerHeadCache {

    private static final long CACHE_TTL_SECONDS = 30L * 24L * 60L * 60L;

    private final File cacheFile;
    private final YamlConfiguration cacheConfiguration;
    private final ConcurrentMap<UUID, CachedHead> memoryCache = new ConcurrentHashMap<>();

    public PlayerHeadCache(final WildEconomyPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Failed to create plugin data folder for player head cache");
        }

        this.cacheFile = new File(plugin.getDataFolder(), "player-head-cache.yml");
        this.cacheConfiguration = YamlConfiguration.loadConfiguration(this.cacheFile);
    }

    public ItemStack getBaseHead(final Player player) {
        Objects.requireNonNull(player, "player");

        final UUID playerId = player.getUniqueId();
        final long now = Instant.now().getEpochSecond();

        final CachedHead inMemory = this.memoryCache.get(playerId);
        if (inMemory != null && !this.isExpired(inMemory.updatedAtEpochSecond(), now)) {
            return inMemory.itemStack().clone();
        }

        synchronized (this) {
            final CachedHead rechecked = this.memoryCache.get(playerId);
            if (rechecked != null && !this.isExpired(rechecked.updatedAtEpochSecond(), now)) {
                return rechecked.itemStack().clone();
            }

            final CachedHead fromDisk = this.loadFromDisk(playerId);
            if (fromDisk != null && !this.isExpired(fromDisk.updatedAtEpochSecond(), now)) {
                this.memoryCache.put(playerId, fromDisk);
                return fromDisk.itemStack().clone();
            }

            final ItemStack freshHead = this.buildFreshHead(player);
            final CachedHead cachedHead = new CachedHead(freshHead.clone(), now);
            this.memoryCache.put(playerId, cachedHead);
            this.saveToDisk(playerId, freshHead, now, player.getName());
            return freshHead.clone();
        }
    }

    private CachedHead loadFromDisk(final UUID playerId) {
        final String basePath = "heads." + playerId;
        final ItemStack itemStack = this.cacheConfiguration.getItemStack(basePath + ".item");
        final long updatedAt = this.cacheConfiguration.getLong(basePath + ".updated-at", 0L);
        if (itemStack == null || updatedAt <= 0L) {
            return null;
        }
        return new CachedHead(itemStack, updatedAt);
    }

    private void saveToDisk(
        final UUID playerId,
        final ItemStack itemStack,
        final long updatedAtEpochSecond,
        final String playerName
    ) {
        final String basePath = "heads." + playerId;
        this.cacheConfiguration.set(basePath + ".item", itemStack);
        this.cacheConfiguration.set(basePath + ".updated-at", updatedAtEpochSecond);
        this.cacheConfiguration.set(basePath + ".player-name", playerName);

        try {
            this.cacheConfiguration.save(this.cacheFile);
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to persist player head cache for " + playerId, exception);
        }
    }

    private ItemStack buildFreshHead(final Player player) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta rawMeta = head.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) {
            return head;
        }

        meta.setOwningPlayer(player);
        meta.setDisplayName(player.getName());
        head.setItemMeta(meta);
        return head;
    }

    private boolean isExpired(final long updatedAtEpochSecond, final long nowEpochSecond) {
        return updatedAtEpochSecond + CACHE_TTL_SECONDS <= nowEpochSecond;
    }

    private record CachedHead(ItemStack itemStack, long updatedAtEpochSecond) {}
}
