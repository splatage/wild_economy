package com.splatage.wild_economy.economy.service;

import com.splatage.wild_economy.economy.model.MoneyAmount;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BalanceCache {

    private final ConcurrentMap<UUID, CachedBalance> entries = new ConcurrentHashMap<>();

    public Optional<CachedBalance> get(final UUID playerId) {
        return Optional.ofNullable(this.entries.get(playerId));
    }

    public void put(final UUID playerId, final MoneyAmount balance, final long refreshedAtEpochSecond, final boolean online) {
        this.entries.put(playerId, new CachedBalance(balance, refreshedAtEpochSecond, online));
    }

    public boolean isOnline(final UUID playerId) {
        final CachedBalance cachedBalance = this.entries.get(playerId);
        return cachedBalance != null && cachedBalance.online();
    }

    public void evict(final UUID playerId) {
        this.entries.remove(playerId);
    }

    public void clear() {
        this.entries.clear();
    }

    public record CachedBalance(
        MoneyAmount balance,
        long refreshedAtEpochSecond,
        boolean online
    ) {}
}
