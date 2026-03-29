package com.splatage.wild_economy.gui.browse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.stock.StockMetricsSnapshot;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.StockState;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.pricing.PricingServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseService;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeCatalogView;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import com.splatage.wild_economy.gui.layout.LayoutChildDefinition;
import com.splatage.wild_economy.gui.layout.LayoutGroupDefinition;
import com.splatage.wild_economy.gui.layout.LayoutPlacementResolver;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ExchangeLayoutBrowseServiceImplTest {

    private static final ItemKey ITEM_KEY = new ItemKey("minecraft:oak_log");

    @Test
    void browseLayout_reusesCachedVisibleEntriesWithinSameStockRevision() {
        final ExchangeCatalog catalog = catalog(playerStockedEntry());
        final PricingService pricingService = new PricingServiceImpl(catalog);
        final CountingStockService stockService = new CountingStockService(
            new StockSnapshot(ITEM_KEY, 75L, 100L, 0.75D, StockState.HEALTHY)
        );
        final ExchangeBrowseService browseService = new ExchangeBrowseServiceImpl(catalog, stockService, pricingService);
        final LayoutBlueprint layoutBlueprint = layoutBlueprint();
        final ExchangeLayoutBrowseServiceImpl layoutBrowseService = new ExchangeLayoutBrowseServiceImpl(
            catalog,
            browseService,
            stockService,
            layoutBlueprint,
            new LayoutPlacementResolver(layoutBlueprint)
        );

        final List<ExchangeCatalogView> first = layoutBrowseService.browseLayout("blocks", "logs", 0, 10);
        final List<ExchangeCatalogView> second = layoutBrowseService.browseLayout("blocks", "logs", 0, 10);

        assertEquals(1, stockService.snapshotCalls(), "Stock snapshot should be resolved once for the cached browse result");
        assertEquals(first, second, "Browse results should be stable across repeated calls within the same stock revision");

        layoutBrowseService.shutdown();
    }

    @Test
    void closeAfterDirtyRevision_prewarmsVisibleCachesBeforeNextOpen() throws Exception {
        final ExchangeCatalog catalog = catalog(playerStockedEntry());
        final PricingService pricingService = new PricingServiceImpl(catalog);
        final CountingStockService stockService = new CountingStockService(
            new StockSnapshot(ITEM_KEY, 75L, 100L, 0.75D, StockState.HEALTHY)
        );
        final ExchangeBrowseService browseService = new ExchangeBrowseServiceImpl(catalog, stockService, pricingService);
        final ExchangeLayoutBrowseServiceImpl layoutBrowseService = new ExchangeLayoutBrowseServiceImpl(
            catalog,
            browseService,
            stockService,
            layoutBlueprint(),
            new LayoutPlacementResolver(layoutBlueprint())
        );

        layoutBrowseService.browseLayout("blocks", "logs", 0, 10);
        assertEquals(1, stockService.snapshotCalls());

        stockService.setSnapshot(new StockSnapshot(ITEM_KEY, 60L, 100L, 0.60D, StockState.HEALTHY));
        stockService.setRevision(2L);

        final UUID viewerId = UUID.randomUUID();
        layoutBrowseService.handleExchangeViewOpened(viewerId);
        layoutBrowseService.handleExchangeViewClosed(viewerId);

        Thread.sleep(150L);

        assertEquals(2, stockService.snapshotCalls(), "Close-triggered prewarm should rebuild the visible caches once for the dirty revision");

        layoutBrowseService.browseLayout("blocks", "logs", 0, 10);
        assertEquals(2, stockService.snapshotCalls(), "Next open after prewarm should reuse the warmed visible cache");

        layoutBrowseService.shutdown();
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

    private static LayoutBlueprint layoutBlueprint() {
        return new LayoutBlueprint(
            Map.of(
                "blocks",
                new LayoutGroupDefinition(
                    "blocks",
                    "Blocks",
                    0,
                    "OAK_LOG",
                    Map.of(
                        "logs",
                        new LayoutChildDefinition(
                            "logs",
                            "Logs",
                            0,
                            "OAK_LOG",
                            List.of(ITEM_KEY.value()),
                            List.of()
                        )
                    ),
                    List.of(),
                    List.of()
                )
            ),
            Map.of()
        );
    }

    private static final class CountingStockService implements StockService {
        private volatile StockSnapshot snapshot;
        private final AtomicInteger snapshotCalls = new AtomicInteger();
        private final AtomicInteger revision = new AtomicInteger(1);

        private CountingStockService(final StockSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private void setSnapshot(final StockSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private void setRevision(final long revision) {
            this.revision.set((int) revision);
        }

        @Override
        public StockSnapshot getSnapshot(final ItemKey itemKey) {
            this.snapshotCalls.incrementAndGet();
            return this.snapshot;
        }

        private int snapshotCalls() { return this.snapshotCalls.get(); }

        @Override public long getAvailableRoom(final ItemKey itemKey) { throw new UnsupportedOperationException(); }
        @Override public void addStock(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public boolean tryConsume(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public int consumeUpTo(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public void removeStock(final ItemKey itemKey, final int amount) { throw new UnsupportedOperationException(); }
        @Override public long cacheRevision() { return this.revision.get(); }
        @Override public void flushDirtyNow() { throw new UnsupportedOperationException(); }
        @Override public StockMetricsSnapshot metricsSnapshot() { throw new UnsupportedOperationException(); }
        @Override public void shutdown() { throw new UnsupportedOperationException(); }
    }
}
