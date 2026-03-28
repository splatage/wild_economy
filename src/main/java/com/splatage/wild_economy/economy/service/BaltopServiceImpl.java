package com.splatage.wild_economy.economy.service;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.model.BalanceRankEntry;
import com.splatage.wild_economy.economy.model.EconomyAccountRecord;
import com.splatage.wild_economy.economy.repository.EconomyAccountRepository;
import com.splatage.wild_economy.economy.repository.EconomyNameCacheRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BaltopServiceImpl implements BaltopService {

    private final EconomyAccountRepository economyAccountRepository;
    private final EconomyNameCacheRepository economyNameCacheRepository;
    private final EconomyConfig economyConfig;
    private final ConcurrentMap<String, CachedPage> pageCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CachedRank> rankCache = new ConcurrentHashMap<>();

    public BaltopServiceImpl(
        final EconomyAccountRepository economyAccountRepository,
        final EconomyNameCacheRepository economyNameCacheRepository,
        final EconomyConfig economyConfig
    ) {
        this.economyAccountRepository = Objects.requireNonNull(economyAccountRepository, "economyAccountRepository");
        this.economyNameCacheRepository = Objects.requireNonNull(economyNameCacheRepository, "economyNameCacheRepository");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    @Override
    public List<BalanceRankEntry> getPage(final int page, final int pageSize) {
        final int safePage = Math.max(1, page);
        final int safePageSize = Math.max(1, pageSize);
        final String cacheKey = safePage + ":" + safePageSize;
        final long now = System.currentTimeMillis() / 1000L;

        final CachedPage cachedPage = this.pageCache.get(cacheKey);
        if (cachedPage != null && cachedPage.expiresAtEpochSecond() > now) {
            return cachedPage.entries();
        }

        final int offset = (safePage - 1) * safePageSize;
        final List<EconomyAccountRecord> accounts = this.economyAccountRepository.findTopAccounts(safePageSize, offset);

        final List<UUID> playerIds = accounts.stream()
                .map(EconomyAccountRecord::playerId)
                .toList();
        final Map<UUID, String> lastKnownNames = this.economyNameCacheRepository.findLastKnownNames(playerIds);

        final List<BalanceRankEntry> result = new ArrayList<>(accounts.size());
        for (int index = 0; index < accounts.size(); index++) {
            final EconomyAccountRecord account = accounts.get(index);
            result.add(new BalanceRankEntry(
                    offset + index + 1,
                    account.playerId(),
                    lastKnownNames.get(account.playerId()),
                    account.balance()
            ));
        }

        final List<BalanceRankEntry> immutable = List.copyOf(result);
        this.pageCache.put(cacheKey, new CachedPage(immutable, now + Math.max(1, this.economyConfig.offlineCacheSeconds())));
        return immutable;
    }

    @Override
    public int getRank(final UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        final long now = System.currentTimeMillis() / 1000L;
        final CachedRank cachedRank = this.rankCache.get(playerId);
        if (cachedRank != null && cachedRank.expiresAtEpochSecond() > now) {
            return cachedRank.rank();
        }

        final int rank = Math.max(0, this.economyAccountRepository.findRank(playerId));
        this.rankCache.put(playerId, new CachedRank(rank, now + Math.max(1, this.economyConfig.offlineCacheSeconds())));
        return rank;
    }

    @Override
    public void invalidate() {
        this.pageCache.clear();
        this.rankCache.clear();
    }

    private record CachedPage(List<BalanceRankEntry> entries, long expiresAtEpochSecond) {}
    private record CachedRank(int rank, long expiresAtEpochSecond) {}
}
