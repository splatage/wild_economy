package com.splatage.wild_economy.config;

import com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet;
import com.splatage.wild_economy.title.model.TitleOption;
import com.splatage.wild_economy.title.model.TitleSection;
import com.splatage.wild_economy.title.model.TitleSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitleSettingsConfigTest {

    @Test
    void titlesInSectionFamily_ordersRelicTitlesByTierThenSlot() {
        final TitleOption tierThree = option("stormbound_three", "Tempeststride", 18, "stormbound", 3, 30, TitleSection.RELIC, TitleSource.RELIC);
        final TitleOption tierOne = option("stormbound_one", "Galefoot", 0, "stormbound", 1, 10, TitleSection.RELIC, TitleSource.RELIC);
        final TitleOption tierTwo = option("stormbound_two", "Galecrest", 9, "stormbound", 2, 20, TitleSection.RELIC, TitleSource.RELIC);

        final Map<String, TitleOption> titles = new LinkedHashMap<>();
        titles.put(tierThree.key(), tierThree);
        titles.put(tierOne.key(), tierOne);
        titles.put(tierTwo.key(), tierTwo);
        final TitleSettingsConfig config = new TitleSettingsConfig(titles);

        assertEquals(List.of(tierOne, tierTwo, tierThree), config.titlesInSectionFamily(TitleSection.RELIC, "stormbound"));
    }

    @Test
    void orderedTitles_preservesPriorityForAutomaticResolution() {
        final TitleOption lowerPriority = option("low", "Low", 0, "market", 1, 10, TitleSection.BEST_OF_ALL_TIME, TitleSource.COMMERCE_MILESTONE);
        final TitleOption higherPriority = option("high", "High", 40, "market", 1, 90, TitleSection.BEST_OF_ALL_TIME, TitleSource.COMMERCE_MILESTONE);

        final Map<String, TitleOption> titles = new LinkedHashMap<>();
        titles.put(lowerPriority.key(), lowerPriority);
        titles.put(higherPriority.key(), higherPriority);
        final TitleSettingsConfig config = new TitleSettingsConfig(titles);

        assertEquals(List.of(higherPriority, lowerPriority), config.orderedTitles());
    }

    @Test
    void familiesInSection_preservesFirstConfiguredOrder() {
        final TitleOption market = option("market", "Market Laureate", 0, "market", 1, 10, TitleSection.BEST_OF_ALL_TIME, TitleSource.COMMERCE_MILESTONE);
        final TitleOption bread = option("bread", "Breadmaster", 1, "bread", 1, 10, TitleSection.BEST_OF_ALL_TIME, TitleSource.COMMERCE_MILESTONE);
        final TitleOption marketSecond = option("market_2", "Merchant Prince", 2, "market", 2, 20, TitleSection.BEST_OF_ALL_TIME, TitleSource.COMMERCE_MILESTONE);

        final Map<String, TitleOption> titles = new LinkedHashMap<>();
        titles.put(market.key(), market);
        titles.put(bread.key(), bread);
        titles.put(marketSecond.key(), marketSecond);
        final TitleSettingsConfig config = new TitleSettingsConfig(titles);

        assertEquals(List.of("market", "bread"), config.familiesInSection(TitleSection.BEST_OF_ALL_TIME));
    }

    private static TitleOption option(
            final String key,
            final String displayName,
            final int slot,
            final String family,
            final int tier,
            final int priority,
            final TitleSection section,
            final TitleSource source
    ) {
        return new TitleOption(
                key,
                displayName,
                displayName,
                "NAME_TAG",
                slot,
                source,
                section,
                family,
                tier,
                priority,
                List.of(),
                List.of(),
                StoreVisibilityWhenUnmet.SHOW_LOCKED,
                null
        );
    }
}
