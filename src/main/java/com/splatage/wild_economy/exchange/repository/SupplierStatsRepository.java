package com.splatage.wild_economy.exchange.repository;

import com.splatage.wild_economy.exchange.supplier.SupplierAggregateRow;
import java.util.List;
import java.util.UUID;

public interface SupplierStatsRepository {

    List<SupplierAggregateRow> loadAllTimeRows();

    List<SupplierAggregateRow> loadWeeklyRows(String weekKey);

    void recordSaleContribution(String weekKey, UUID playerId, String itemKey, long quantitySold, long updatedAtEpochSecond);
}
