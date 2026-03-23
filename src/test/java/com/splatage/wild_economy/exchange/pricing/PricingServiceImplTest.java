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
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.StockState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PricingServiceImplTest {

    private static final ItemKey WHEAT = new ItemKey("minecraft:wheat");

    @Test
    void quoteSell_spansPlateauLinearAndFloorSegments() {
        final PricingServiceImpl pricingService = new PricingServiceImpl(
            this.catalogWith(this.entry(
                new BigDecimal("12.00"),
                new BigDecimal("10.00"),
                List.of(new SellPriceBand(5L, 15L, new BigDecimal("2.00")))
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
    void quoteSell_withoutEnvelopeUsesFlatSellPrice() {
        final PricingServiceImpl pricingService = new PricingServiceImpl(
            this.catalogWith(this.entry(
                new BigDecimal("12.00"),
                new BigDecimal("8.00"),
                List.of()
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
    void quoteSell_clampsConfiguredFloorPriceToSellPrice() {
        final PricingServiceImpl pricingService = new PricingServiceImpl(
            this.catalogWith(this.entry(
                new BigDecimal("12.00"),
                new BigDecimal("10.00"),
                List.of(new SellPriceBand(0L, 10L, new BigDecimal("12.00")))
            ))
        );

        final StockSnapshot snapshot = new StockSnapshot(WHEAT, 0L, 100L, 0.0D, StockState.OUT_OF_STOCK);
        final SellQuote quote = pricingService.quoteSell(WHEAT, 20, snapshot);

        assertEquals(new BigDecimal("10.00"), quote.baseUnitPrice());
        assertEquals(new BigDecimal("10.00"), quote.effectiveUnitPrice());
        assertEquals(new BigDecimal("200.00"), quote.totalPrice());
        assertFalse(quote.tapered());
    }

    private ExchangeCatalog catalogWith(final ExchangeCatalogEntry entry) {
        return new ExchangeCatalog(Map.of(entry.itemKey(), entry));
    }

    private ExchangeCatalogEntry entry(
        final BigDecimal buyPrice,
        final BigDecimal sellPrice,
        final List<SellPriceBand> sellPriceBands
    ) {
        return new ExchangeCatalogEntry(
            WHEAT,
            "Wheat",
            ItemCategory.FARMING_AND_FOOD,
            GeneratedItemCategory.FARMING,
            ItemPolicyMode.PLAYER_STOCKED,
            new BigDecimal("8.00"),
            buyPrice,
            sellPrice,
            3456L,
            250L,
            64L,
            sellPriceBands,
            true,
            true
        );
    }
}
