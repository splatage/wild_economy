package com.splatage.wild_economy.exchange.supplier;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierStatsService {

    void recordSale(UUID playerId, ItemKey itemKey, int amount);

    List<TopSupplierEntry> getTopSuppliers(SupplierScope scope, int limit);

    Optional<SupplierPlayerDetail> getPlayerDetail(SupplierScope scope, UUID playerId, int topItemsLimit);

    Optional<UUID> findPlayerIdByName(String playerName);

    void shutdown();
}
