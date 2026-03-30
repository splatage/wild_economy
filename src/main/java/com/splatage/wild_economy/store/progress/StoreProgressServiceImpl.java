package com.splatage.wild_economy.store.progress;

import com.splatage.wild_economy.WildEconomyPlugin;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class StoreProgressServiceImpl implements StoreProgressService {

    private static final Pattern COUNTER_KEY_PATTERN = Pattern.compile("^[a-z0-9._/-]+$");

    private final WildEconomyPlugin plugin;
    private final Map<String, NamespacedKey> counterKeyCache = new ConcurrentHashMap<>();

    public StoreProgressServiceImpl(final WildEconomyPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean hasAdvancement(final Player player, final String advancementKey) {
        Objects.requireNonNull(player, "player");
        final NamespacedKey namespacedKey = NamespacedKey.fromString(Objects.requireNonNull(advancementKey, "advancementKey"));
        if (namespacedKey == null) {
            throw new IllegalStateException("Invalid Store advancement key: " + advancementKey);
        }
        final Advancement advancement = Bukkit.getAdvancement(namespacedKey);
        if (advancement == null) {
            throw new IllegalStateException("Unknown Store advancement requirement: " + advancementKey);
        }
        return player.getAdvancementProgress(advancement).isDone();
    }

    @Override
    public long getCustomCounter(final Player player, final String counterKey) {
        Objects.requireNonNull(player, "player");
        final PersistentDataContainer container = player.getPersistentDataContainer();
        final Long stored = container.get(this.namespacedCounterKey(counterKey), PersistentDataType.LONG);
        return stored == null ? 0L : stored;
    }

    @Override
    public long incrementCustomCounter(final Player player, final String counterKey, final long delta) {
        Objects.requireNonNull(player, "player");
        if (delta < 0L) {
            throw new IllegalArgumentException("delta cannot be negative");
        }
        if (delta == 0L) {
            return this.getCustomCounter(player, counterKey);
        }
        final long current = this.getCustomCounter(player, counterKey);
        final long updated;
        try {
            updated = Math.addExact(current, delta);
        } catch (final ArithmeticException ignored) {
            this.setCustomCounter(player, counterKey, Long.MAX_VALUE);
            return Long.MAX_VALUE;
        }
        this.setCustomCounter(player, counterKey, updated);
        return updated;
    }

    @Override
    public void setCustomCounter(final Player player, final String counterKey, final long value) {
        Objects.requireNonNull(player, "player");
        if (value < 0L) {
            throw new IllegalArgumentException("value cannot be negative");
        }
        player.getPersistentDataContainer().set(this.namespacedCounterKey(counterKey), PersistentDataType.LONG, value);
    }

    private NamespacedKey namespacedCounterKey(final String rawCounterKey) {
        final String normalizedCounterKey = this.normalizeCounterKey(rawCounterKey);
        return this.counterKeyCache.computeIfAbsent(
            normalizedCounterKey,
            key -> new NamespacedKey(this.plugin, "store_counter." + key)
        );
    }

    private String normalizeCounterKey(final String rawCounterKey) {
        final String normalized = Objects.requireNonNull(rawCounterKey, "counterKey")
            .trim()
            .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("counterKey cannot be blank");
        }
        if (!COUNTER_KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "counterKey must contain only lowercase letters, numbers, '.', '_', '-', or '/' characters: " + rawCounterKey
            );
        }
        return normalized;
    }
}
