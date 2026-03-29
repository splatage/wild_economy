package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ExchangeBrowseServiceImpl implements ExchangeBrowseService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;
    private final PricingService pricingService;
    private final Map<ItemKey, ExchangeItemView> itemViewCache;
    private volatile long cachedStockRevision;

    public ExchangeBrowseServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService,
        final PricingService pricingService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.itemViewCache = new ConcurrentHashMap<>();
        this.cachedStockRevision = Long.MIN_VALUE;
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        this.resetCachesIfRevisionChanged();
        return this.itemViewCache.computeIfAbsent(itemKey, this::buildItemView);
    }

    private ExchangeItemView buildItemView(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));
        final StockSnapshot snapshot = this.stockService.getSnapshot(itemKey);
        return new ExchangeItemView(
            itemKey,
            entry.displayName(),
            this.resolveCurrentBuyPrice(entry, snapshot),
            snapshot.stockCount(),
            snapshot.stockCap(),
            snapshot.stockState(),
            entry.buyEnabled()
        );
    }

    private void resetCachesIfRevisionChanged() {
        final long currentRevision = this.stockService.cacheRevision();
        if (currentRevision == this.cachedStockRevision) {
            return;
        }
        synchronized (this) {
            if (currentRevision == this.cachedStockRevision) {
                return;
            }
            this.itemViewCache.clear();
            this.cachedStockRevision = currentRevision;
        }
    }

    private BigDecimal resolveCurrentBuyPrice(
        final ExchangeCatalogEntry entry,
        final StockSnapshot snapshot
    ) {
        return this.pricingService.quoteBuy(entry.itemKey(), 1, snapshot).unitPrice();
    }
}
