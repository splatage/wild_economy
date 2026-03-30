package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeCatalogDerivedWorthTest {

    @Test
    void derivesFireworkRocketWorthFromPaperAndGunpowder() {
        final ExchangeCatalog catalog = new ExchangeCatalog(entries(
            entry("minecraft:firework_rocket", "Firework Rocket", bd("11.00"), eco("16.50", "8.80", "8.80", "4.40")),
            entry("minecraft:paper", "Paper", bd("3.00"), eco("4.50", "2.40", "2.40", "1.20")),
            entry("minecraft:gunpowder", "Gunpowder", bd("8.00"), eco("12.00", "6.40", "6.40", "3.20"))
        ));

        final ExchangeCatalogEntry flightThree = catalog.get(new ItemKey("minecraft:firework_rocket/flight_3")).orElseThrow();
        assertEquals(bd("9.00"), flightThree.baseWorth());
        assertEquals(bd("13.50"), flightThree.eco().buyPriceLowStock());
        assertEquals(bd("7.20"), flightThree.eco().buyPriceHighStock());
        assertEquals(bd("7.20"), flightThree.eco().sellPriceLowStock());
        assertEquals(bd("3.60"), flightThree.eco().sellPriceHighStock());
    }

    @Test
    void derivesSplashPotionWorthFromPotionBaseAndBrewingIngredients() {
        final Map<ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>();
        entries.put(new ItemKey("minecraft:splash_potion"), entry(
            "minecraft:splash_potion",
            "Splash Potion",
            bd("45.00"),
            eco("54.00", "36.00", "36.00", "27.00")
        ));
        entries.put(new ItemKey("minecraft:potion"), entry(
            "minecraft:potion",
            "Potion",
            bd("100.00"),
            eco("120.00", "80.00", "80.00", "60.00")
        ));
        entries.put(new ItemKey("minecraft:gunpowder"), entry(
            "minecraft:gunpowder",
            "Gunpowder",
            bd("8.00"),
            eco("12.00", "6.40", "6.40", "3.20")
        ));
        entries.put(new ItemKey("minecraft:glistering_melon_slice"), entry(
            "minecraft:glistering_melon_slice",
            "Glistering Melon Slice",
            bd("6.00"),
            eco("9.00", "4.80", "4.80", "2.40")
        ));
        entries.put(new ItemKey("minecraft:fermented_spider_eye"), entry(
            "minecraft:fermented_spider_eye",
            "Fermented Spider Eye",
            bd("7.00"),
            eco("10.50", "5.60", "5.60", "2.80")
        ));
        entries.put(new ItemKey("minecraft:glowstone_dust"), entry(
            "minecraft:glowstone_dust",
            "Glowstone Dust",
            bd("4.00"),
            eco("6.00", "3.20", "3.20", "1.60")
        ));

        final RootValueLookup emptyLookup = key -> Optional.empty();
        final ExchangeCatalog catalog = new ExchangeCatalog(entries, emptyLookup);
        final ExchangeCatalogEntry harming = catalog.get(new ItemKey("minecraft:splash_potion/harming/strong")).orElseThrow();

        assertEquals(bd("125.00"), harming.baseWorth());
        assertEquals(bd("150.00"), harming.eco().buyPriceLowStock());
        assertEquals(bd("100.00"), harming.eco().buyPriceHighStock());
        assertEquals(bd("100.00"), harming.eco().sellPriceLowStock());
        assertEquals(bd("75.00"), harming.eco().sellPriceHighStock());
    }

    @Test
    void fallsBackToFamilyBaseWorthWhenIngredientsAreUnavailable() {
        final ExchangeCatalog catalog = new ExchangeCatalog(entries(
            entry("minecraft:potion", "Potion", bd("110.00"), eco("132.00", "88.00", "88.00", "66.00"))
        ));

        final Optional<ExchangeCatalogEntry> derived = catalog.get(new ItemKey("minecraft:potion/swiftness/normal"));
        assertTrue(derived.isPresent());
        assertEquals(bd("110.00"), derived.orElseThrow().baseWorth());
        assertEquals(bd("132.00"), derived.orElseThrow().eco().buyPriceLowStock());
    }

    private static Map<ItemKey, ExchangeCatalogEntry> entries(final ExchangeCatalogEntry... entries) {
        final Map<ItemKey, ExchangeCatalogEntry> values = new LinkedHashMap<>();
        for (final ExchangeCatalogEntry entry : entries) {
            values.put(entry.itemKey(), entry);
        }
        return values;
    }

    private static ExchangeCatalogEntry entry(
        final String key,
        final String displayName,
        final BigDecimal baseWorth,
        final ExchangeCatalogEntry.ResolvedEcoEntry eco
    ) {
        return new ExchangeCatalogEntry(
            new ItemKey(key),
            displayName,
            ItemCategory.REDSTONE_AND_UTILITY,
            null,
            ItemPolicyMode.PLAYER_STOCKED,
            baseWorth,
            200L,
            50L,
            true,
            true,
            eco
        );
    }

    private static ExchangeCatalogEntry.ResolvedEcoEntry eco(
        final String buyLow,
        final String buyHigh,
        final String sellLow,
        final String sellHigh
    ) {
        return new ExchangeCatalogEntry.ResolvedEcoEntry(
            0L,
            200L,
            bd(buyLow),
            bd(buyHigh),
            bd(sellLow),
            bd(sellHigh)
        );
    }

    private static BigDecimal bd(final String value) {
        return new BigDecimal(value).setScale(2);
    }
}
