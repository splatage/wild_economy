package com.splatage.wild_economy.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ConfigValidatorTest {

    private static final ItemKey WHEAT = new ItemKey("minecraft:wheat");

    @Test
    void validate_acceptsResolvedEcoSetup() {
        final ExchangeItemsConfig exchangeItemsConfig = new ExchangeItemsConfig(Map.of(
                WHEAT,
                new ExchangeItemsConfig.RawItemEntry(
                        WHEAT,
                        "Wheat",
                        ItemCategory.FARMING_AND_FOOD,
                        GeneratedItemCategory.FARMING,
                        ItemPolicyMode.PLAYER_STOCKED,
                        true,
                        true,
                        3456L,
                        250L,
                        new BigDecimal("8.00"),
                        new ExchangeItemsConfig.ResolvedEcoEntry(
                                64L,
                                3456L,
                                new BigDecimal("12.00"),
                                new BigDecimal("10.00"),
                                new BigDecimal("8.00"),
                                new BigDecimal("1.60")
                        )
                )
        ));

        final ExchangeCatalog exchangeCatalog = new ExchangeCatalog(Map.of(
                WHEAT,
                this.entry(
                        64L,
                        3456L,
                        new BigDecimal("12.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("8.00"),
                        new BigDecimal("1.60"),
                        3456L,
                        250L,
                        true,
                        true
                )
        ));

        assertDoesNotThrow(() -> new ConfigValidator(
                exchangeItemsConfig,
                exchangeCatalog
        ).validate());
    }

    @Test
    void validate_rejectsPublishedRuntimeArbitrage() {
        final ExchangeItemsConfig exchangeItemsConfig = new ExchangeItemsConfig(Map.of(
                WHEAT,
                new ExchangeItemsConfig.RawItemEntry(
                        WHEAT,
                        "Wheat",
                        ItemCategory.FARMING_AND_FOOD,
                        GeneratedItemCategory.FARMING,
                        ItemPolicyMode.PLAYER_STOCKED,
                        true,
                        true,
                        3456L,
                        250L,
                        new BigDecimal("8.00"),
                        new ExchangeItemsConfig.ResolvedEcoEntry(
                                64L,
                                3456L,
                                new BigDecimal("12.00"),
                                new BigDecimal("10.00"),
                                new BigDecimal("8.00"),
                                new BigDecimal("11.00")
                        )
                )
        ));

        final ExchangeCatalog exchangeCatalog = new ExchangeCatalog(Map.of(
                WHEAT,
                this.entry(
                        64L,
                        3456L,
                        new BigDecimal("12.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("8.00"),
                        new BigDecimal("1.60"),
                        3456L,
                        250L,
                        true,
                        true
                )
        ));

        final IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ConfigValidator(
                        exchangeItemsConfig,
                        exchangeCatalog
                ).validate()
        );

        assertTrue(exception.getMessage().contains("arbitrage risk"));
    }

    @Test
    void validate_rejectsMergedCatalogArbitrage() {
        final ExchangeCatalog exchangeCatalog = new ExchangeCatalog(Map.of(
                WHEAT,
                this.entry(
                        64L,
                        3456L,
                        new BigDecimal("5.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("6.00"),
                        new BigDecimal("6.00"),
                        3456L,
                        250L,
                        true,
                        true
                )
        ));

        final IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ConfigValidator(
                        new ExchangeItemsConfig(Map.of()),
                        exchangeCatalog
                ).validate()
        );

        assertTrue(exception.getMessage().contains("arbitrage risk"));
    }

    private ExchangeCatalogEntry entry(
            final long minStockInclusive,
            final long maxStockInclusive,
            final BigDecimal buyPriceLowStock,
            final BigDecimal buyPriceHighStock,
            final BigDecimal sellPriceLowStock,
            final BigDecimal sellPriceHighStock,
            final long stockCap,
            final long turnoverAmountPerInterval,
            final boolean buyEnabled,
            final boolean sellEnabled
    ) {
        return new ExchangeCatalogEntry(
                WHEAT,
                "Wheat",
                ItemCategory.FARMING_AND_FOOD,
                GeneratedItemCategory.FARMING,
                ItemPolicyMode.PLAYER_STOCKED,
                new BigDecimal("8.00"),
                stockCap,
                turnoverAmountPerInterval,
                buyEnabled,
                sellEnabled,
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
