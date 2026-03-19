package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import java.util.Objects;

public final class StockTurnoverServiceImpl implements StockTurnoverService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;
    private final TransactionLogService transactionLogService;

    public StockTurnoverServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService,
        final TransactionLogService transactionLogService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.transactionLogService = Objects.requireNonNull(transactionLogService, "transactionLogService");
    }

    @Override
    public void runTurnoverPass() {
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.allEntries()) {
            if (entry.policyMode() != ItemPolicyMode.PLAYER_STOCKED) {
                continue;
            }
            final long turnover = Math.max(0L, entry.turnoverAmountPerInterval());
            if (turnover <= 0L) {
                continue;
            }

            final var snapshot = this.stockService.getSnapshot(entry.itemKey());
            if (snapshot.stockCount() <= 0L) {
                continue;
            }

            final int removeAmount = (int) Math.min(snapshot.stockCount(), turnover);
            if (removeAmount <= 0) {
                continue;
            }

            this.stockService.removeStock(entry.itemKey(), removeAmount);
            this.transactionLogService.logTurnover(entry.itemKey(), removeAmount);
        }
    }
}
