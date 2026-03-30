package com.splatage.wild_economy.exchange.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.StockState;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.pricing.PricingServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockMetricsSnapshot;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExchangeBrowseServiceImplTest {

    private static final ItemKey ITEM_KEY = new ItemKey("minecraft:oak_log");

    @Test
    void getItemView_usesCanonicalPricingServiceForPlayerStockedItems() {
        final ExchangeCatalog catalog = catalog(playerStockedEntry());
        final PricingService pricingService = new PricingServiceImpl(catalog);
        final StockSnapshot snapshot = new StockSnapshot(ITEM_KEY, 75L, 100L, 0.75D, StockState.HEALTHY);
        final ExchangeBrowseServiceImpl browseService = new ExchangeBrowseServiceImpl(
            catalog,
            new FixedSnapshotStockService(snapshot),
            pricingService
        );

        final ExchangeItemView itemView = browseService.getItemView(ITEM_KEY);

        assertEquals(
            pricingService.quoteBuy(ITEM_KEY, 1, snapshot).unitPrice(),
            itemView.buyPrice(),
            "Item view pricing should come from the canonical pricing service"
        );
    }

    @Test
    void getItemView_reusesCachedEntryWithinSameStockRevision() {
        final ExchangeCatalog catalog = catalog(playerStockedEntry());
        final PricingService pricingService = new PricingServiceImpl(catalog);
        final CountingStockService stockService = new CountingStockService(
            new StockSnapshot(ITEM_KEY, 75L, 100L, 0.75D, StockState.HEALTHY)
        );
        final ExchangeBrowseServiceImpl browseService = new ExchangeBrowseServiceImpl(
            catalog,
            stockService,
            pricingService
        );

        final ExchangeItemView first = browseService.getItemView(ITEM_KEY);
        final ExchangeItemView second = browseService.getItemView(ITEM_KEY);

        assertEquals(1, stockService.snapshotCalls(), "Stock snapshot should be resolved once for the cached item view");
        assertEquals(first, second, "Item views should be stable across repeated calls within the same stock revision");
    }

    private static ExchangeCatalog catalog(final ExchangeCatalogEntry entry) {
        return new ExchangeCatalog(Map.of(entry.itemKey(), entry));
    }

    private static ExchangeCatalogEntry playerStockedEntry() {
        return new ExchangeCatalogEntry(
            ITEM_KEY,
            "Oak Log",
            ItemCategory.BUILDING_MATERIALS,
            GeneratedItemCategory.WOODS,
            ItemPolicyMode.PLAYER_STOCKED,
            new BigDecimal("4.00"),
            100L,
            10L,
            true,
            true,
            new ExchangeCatalogEntry.ResolvedEcoEntry(
                10L,
                100L,
                new BigDecimal("5.00"),
                new BigDecimal("15.00"),
                new BigDecimal("2.50"),
                new BigDecimal("7.50")
            )
        );
    }

    private static final class FixedSnapshotStockService implements StockService {
        private final StockSnapshot snapshot;

        private FixedSnapshotStockService(final StockSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public StockSnapshot getSnapshot(final ItemKey itemKey) {
            return this.snapshot;
        }

        @Override public long getAvailableRoom(final ItemKey itemKey) { throw new UnsupportedOperationException(); }
        @Override public void addStock(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public boolean tryConsume(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public int consumeUpTo(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public void removeStock(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public long cacheRevision() { return 1L; }
        @Override public Set<ItemKey> stockedItemKeys() { return Set.of(this.snapshot.itemKey()); }
        @Override public void flushDirtyNow() { throw new UnsupportedOperationException(); }
        @Override public StockMetricsSnapshot metricsSnapshot() { throw new UnsupportedOperationException(); }
        @Override public void shutdown() { throw new UnsupportedOperationException(); }
    }

    private static final class CountingStockService implements StockService {
        private final StockSnapshot snapshot;
        private int snapshotCalls;

        private CountingStockService(final StockSnapshot snapshot) { this.snapshot = snapshot; }

        @Override
        public StockSnapshot getSnapshot(final ItemKey itemKey) {
            this.snapshotCalls++;
            return this.snapshot;
        }

        private int snapshotCalls() { return this.snapshotCalls; }

        @Override public long getAvailableRoom(final ItemKey itemKey) { throw new UnsupportedOperationException(); }
        @Override public void addStock(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public boolean tryConsume(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public int consumeUpTo(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public void removeStock(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public long cacheRevision() { return 1L; }
        @Override public Set<ItemKey> stockedItemKeys() { return Set.of(this.snapshot.itemKey()); }
        @Override public void flushDirtyNow() { throw new UnsupportedOperationException(); }
        @Override public StockMetricsSnapshot metricsSnapshot() { throw new UnsupportedOperationException(); }
        @Override public void shutdown() { throw new UnsupportedOperationException(); }
    }
}
