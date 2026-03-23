package com.splatage.wild_economy.exchange.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.StockState;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PricingServiceImplTest {

    private static final ItemKey WHEAT = new ItemKey("minecraft:wheat");

    @Test
    void quoteSell_spansLowPlateauLinearAndHighPlateauSegments() {
        final PricingServiceImpl pricingService = new PricingServiceImpl(
                this.catalogWith(this.entry(
                        new BigDecimal("12.00"),
                        new BigDecimal("12.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("2.00"),
                        5L,
                        15L
                ))
        );

        final StockSnapshot snapshot = new StockSnapshot(WHEAT, 3L, 100L, 0.03D, StockState.LOW);
        final SellQuote quote = pricingService.quoteSell(WHEAT, 20, snapshot);

        assertEquals(new BigDecimal("10.00"), quote.baseUnitPrice());
        assertEquals(new BigDecimal("4.80"), quote.effectiveUnitPrice());
        assertEquals(new BigDecimal("96.00"), quote.totalPrice());
        assertTrue(quote.tapered());
    }

    @Test
    void quoteSell_withFlatResolvedEcoUsesFlatSellPrice() {
        final PricingServiceImpl pricingService = new PricingServiceImpl(
                this.catalogWith(this.entry(
                        new BigDecimal("12.00"),
                        new BigDecimal("12.00"),
                        new BigDecimal("8.00"),
                        new BigDecimal("8.00"),
                        0L,
                        1000L
                ))
        );

        final StockSnapshot snapshot = new StockSnapshot(WHEAT, 500L, 1000L, 0.50D, StockState.HEALTHY);
        final SellQuote quote = pricingService.quoteSell(WHEAT, 7, snapshot);

        assertEquals(new BigDecimal("8.00"), quote.baseUnitPrice());
        assertEquals(new BigDecimal("8.00"), quote.effectiveUnitPrice());
        assertEquals(new BigDecimal("56.00"), quote.totalPrice());
        assertFalse(quote.tapered());
    }

    @Test
    void quoteSell_atOrAboveMaxStockUsesHighStockSellPrice() {
        final PricingServiceImpl pricingService = new PricingServiceImpl(
                this.catalogWith(this.entry(
                        new BigDecimal("12.00"),
                        new BigDecimal("12.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("2.00"),
                        0L,
                        10L
                ))
        );

        final StockSnapshot snapshot = new StockSnapshot(WHEAT, 20L, 100L, 0.20D, StockState.HEALTHY);
        final SellQuote quote = pricingService.quoteSell(WHEAT, 5, snapshot);

        assertEquals(new BigDecimal("2.00"), quote.baseUnitPrice());
        assertEquals(new BigDecimal("2.00"), quote.effectiveUnitPrice());
        assertEquals(new BigDecimal("10.00"), quote.totalPrice());
        assertFalse(quote.tapered());
    }

    private ExchangeCatalog catalogWith(final ExchangeCatalogEntry entry) {
        return new ExchangeCatalog(Map.of(entry.itemKey(), entry));
    }

    private ExchangeCatalogEntry entry(
            final BigDecimal buyPriceLowStock,
            final BigDecimal buyPriceHighStock,
            final BigDecimal sellPriceLowStock,
            final BigDecimal sellPriceHighStock,
            final long minStockInclusive,
            final long maxStockInclusive
    ) {
        return new ExchangeCatalogEntry(
                WHEAT,
                "Wheat",
                ItemCategory.FARMING_AND_FOOD,
                GeneratedItemCategory.FARMING,
                ItemPolicyMode.PLAYER_STOCKED,
                new BigDecimal("8.00"),
                3456L,
                250L,
                true,
                true,
                new ExchangeCatalogEntry.ResolvedEcoEntry(
                        minStockInclusive,
                        maxStockInclusive,
                        buyPriceLowStock,
                        buyPriceHighStock,
                        sellPriceLowStock,
                        sellPriceHighStock
                )
        );
    }
}
