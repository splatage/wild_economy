package com.splatage.wild_economy.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ConfigValidatorTest {

    private static final ItemKey WHEAT = new ItemKey("minecraft:wheat");

    @Test
    void validate_acceptsResolvedLinearEnvelopeSetup() {
        final ExchangeItemsConfig exchangeItemsConfig = new ExchangeItemsConfig(Map.of(
            WHEAT,
            new ExchangeItemsConfig.RawItemEntry(
                WHEAT,
                "Wheat",
                ItemCategory.FARMING_AND_FOOD,
                GeneratedItemCategory.FARMING,
                ItemPolicyMode.PLAYER_STOCKED,
                Boolean.TRUE,
                Boolean.TRUE,
                "default_bulk",
                null,
                null,
                null,
                "default_linear",
                null,
                null,
                List.of()
            )
        ));

        final EcoEnvelopesConfig ecoEnvelopesConfig = new EcoEnvelopesConfig(Map.of(
            "default_linear",
            new EcoEnvelopesConfig.EcoEnvelopeDefinition(
                new BigDecimal("1.50"),
                new BigDecimal("1.00"),
                64L,
                3456L,
                new BigDecimal("0.20")
            )
        ));

        final StockProfilesConfig stockProfilesConfig = new StockProfilesConfig(Map.of(
            "default_bulk",
            new StockProfilesConfig.StockProfileDefinition(3456L, 250L, 64L)
        ));

        final ExchangeCatalog exchangeCatalog = new ExchangeCatalog(Map.of(
            WHEAT,
            this.entry(
                new BigDecimal("12.00"),
                new BigDecimal("8.00"),
                3456L,
                250L,
                64L,
                List.of(new SellPriceBand(64L, 3456L, new BigDecimal("1.60"))),
                true,
                true
            )
        ));

        assertDoesNotThrow(() -> new ConfigValidator(
            exchangeItemsConfig,
            ecoEnvelopesConfig,
            stockProfilesConfig,
            exchangeCatalog
        ).validate());
    }

    @Test
    void validate_rejectsMissingNamedStockProfileReference() {
        final ExchangeItemsConfig exchangeItemsConfig = new ExchangeItemsConfig(Map.of(
            WHEAT,
            new ExchangeItemsConfig.RawItemEntry(
                WHEAT,
                "Wheat",
                ItemCategory.FARMING_AND_FOOD,
                GeneratedItemCategory.FARMING,
                ItemPolicyMode.PLAYER_STOCKED,
                Boolean.TRUE,
                Boolean.TRUE,
                "missing_profile",
                null,
                null,
                null,
                null,
                new BigDecimal("12.00"),
                new BigDecimal("8.00"),
                List.of()
            )
        ));

        final ExchangeCatalog exchangeCatalog = new ExchangeCatalog(Map.of(
            WHEAT,
            this.entry(
                new BigDecimal("12.00"),
                new BigDecimal("8.00"),
                3456L,
                250L,
                64L,
                List.of(new SellPriceBand(64L, 3456L, new BigDecimal("1.60"))),
                true,
                true
            )
        ));

        final IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new ConfigValidator(
                exchangeItemsConfig,
                new EcoEnvelopesConfig(Map.of()),
                new StockProfilesConfig(Map.of()),
                exchangeCatalog
            ).validate()
        );

        assertTrue(exception.getMessage().contains("missing_profile"));
    }

    @Test
    void validate_rejectsMergedCatalogArbitrage() {
        final ExchangeCatalog exchangeCatalog = new ExchangeCatalog(Map.of(
            WHEAT,
            this.entry(
                new BigDecimal("5.00"),
                new BigDecimal("6.00"),
                3456L,
                250L,
                64L,
                List.of(),
                true,
                true
            )
        ));

        final IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new ConfigValidator(
                new ExchangeItemsConfig(Map.of()),
                new EcoEnvelopesConfig(Map.of()),
                new StockProfilesConfig(Map.of()),
                exchangeCatalog
            ).validate()
        );

        assertTrue(exception.getMessage().contains("arbitrage risk"));
    }

    private ExchangeCatalogEntry entry(
        final BigDecimal buyPrice,
        final BigDecimal sellPrice,
        final long stockCap,
        final long turnoverAmountPerInterval,
        final long initialStock,
        final List<SellPriceBand> sellPriceBands,
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
            buyPrice,
            sellPrice,
            stockCap,
            turnoverAmountPerInterval,
            initialStock,
            sellPriceBands,
            buyEnabled,
            sellEnabled
        );
    }
}
