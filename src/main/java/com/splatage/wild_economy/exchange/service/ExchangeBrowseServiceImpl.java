package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ExchangeBrowseServiceImpl implements ExchangeBrowseService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;

    public ExchangeBrowseServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(final ItemCategory category, final int page, final int pageSize) {
        final int safePage = Math.max(0, page);
        final int safePageSize = Math.max(1, pageSize);

        return this.exchangeCatalog.byCategory(category).stream()
            .filter(ExchangeCatalogEntry::buyEnabled)
            .skip((long) safePage * safePageSize)
            .limit(safePageSize)
            .map(entry -> {
                final var snapshot = this.stockService.getSnapshot(entry.itemKey());
                return new ExchangeCatalogView(
                    entry.itemKey(),
                    entry.displayName(),
                    entry.buyPrice(),
                    snapshot.stockCount(),
                    snapshot.stockState()
                );
            })
            .collect(Collectors.toList());
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));
        final var snapshot = this.stockService.getSnapshot(itemKey);

        return new ExchangeItemView(
            itemKey,
            entry.displayName(),
            entry.buyPrice(),
            snapshot.stockCount(),
            snapshot.stockCap(),
            snapshot.stockState(),
            entry.buyEnabled()
        );
    }
}
