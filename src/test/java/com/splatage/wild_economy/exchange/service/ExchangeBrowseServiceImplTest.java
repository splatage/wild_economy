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
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import com.splatage.wild_economy.gui.layout.LayoutChildDefinition;
import com.splatage.wild_economy.gui.layout.LayoutGroupDefinition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
            pricingService,
            layoutBlueprint()
        );

        final ExchangeItemView itemView = browseService.getItemView(ITEM_KEY);

        assertEquals(
            pricingService.quoteBuy(ITEM_KEY, 1, snapshot).unitPrice(),
            itemView.buyPrice(),
            "Browse item pricing should come from the canonical pricing service"
        );
    }

    @Test
    void browseLayout_usesCanonicalPricingServiceForUnlimitedBuyItems() {
        final ExchangeCatalog catalog = catalog(unlimitedBuyEntry());
        final PricingService pricingService = new PricingServiceImpl(catalog);
        final StockSnapshot snapshot = new StockSnapshot(ITEM_KEY, 12L, 100L, 0.12D, StockState.HEALTHY);
        final ExchangeBrowseServiceImpl browseService = new ExchangeBrowseServiceImpl(
            catalog,
            new FixedSnapshotStockService(snapshot),
            pricingService,
            layoutBlueprint()
        );

        final ExchangeCatalogView view = browseService.browseLayout("blocks", "logs", 0, 10).getFirst();

        assertEquals(
            pricingService.quoteBuy(ITEM_KEY, 1, snapshot).unitPrice(),
            view.buyPrice(),
            "Browse layout pricing should come from the canonical pricing service"
        );
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
            "blocks",
            "logs",
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

    private static ExchangeCatalogEntry unlimitedBuyEntry() {
        return new ExchangeCatalogEntry(
            ITEM_KEY,
            "Oak Log",
            ItemCategory.BUILDING_MATERIALS,
            GeneratedItemCategory.WOODS,
            "blocks",
            "logs",
            ItemPolicyMode.UNLIMITED_BUY,
            new BigDecimal("4.00"),
            100L,
            10L,
            true,
            true,
            new ExchangeCatalogEntry.ResolvedEcoEntry(
                10L,
                100L,
                new BigDecimal("6.00"),
                new BigDecimal("18.00"),
                new BigDecimal("3.00"),
                new BigDecimal("9.00")
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

    private static final class FixedSnapshotStockService implements StockService {
        private final StockSnapshot snapshot;

        private FixedSnapshotStockService(final StockSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public StockSnapshot getSnapshot(final ItemKey itemKey) {
            return this.snapshot;
        }

        @Override
        public long getAvailableRoom(final ItemKey itemKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addStock(final ItemKey itemKey, final int amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryConsume(final ItemKey itemKey, final int amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int consumeUpTo(final ItemKey itemKey, final int amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeStock(final ItemKey itemKey, final int amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void flushDirtyNow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public StockMetricsSnapshot metricsSnapshot() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }
    }
}
