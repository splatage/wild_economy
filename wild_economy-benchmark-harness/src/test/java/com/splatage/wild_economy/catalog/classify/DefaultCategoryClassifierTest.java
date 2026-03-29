package com.splatage.wild_economy.catalog.classify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import org.junit.jupiter.api.Test;

final class DefaultCategoryClassifierTest {

    @Test
    void classify_usesWeakFallbackHeuristicsRatherThanMaterialFamilyExpansion() {
        final DefaultCategoryClassifier classifier = new DefaultCategoryClassifier();

        assertEquals(
            CatalogCategory.MOB_DROPS,
            classifier.classify(new ItemFacts(null, "bone", true, false, true, 64, false, false, true))
        );
        assertEquals(
            CatalogCategory.WOODS,
            classifier.classify(new ItemFacts(null, "oak_log", true, true, true, 64, false, false, true))
        );
        assertEquals(
            CatalogCategory.FARMING,
            classifier.classify(new ItemFacts(null, "oak_sapling", true, false, true, 64, false, false, true))
        );
        assertEquals(
            CatalogCategory.TRANSPORT,
            classifier.classify(new ItemFacts(null, "oak_boat", true, false, true, 1, false, false, true))
        );
        assertEquals(
            CatalogCategory.REDSTONE,
            classifier.classify(new ItemFacts(null, "oak_button", true, false, true, 64, false, false, true))
        );
        assertEquals(
            CatalogCategory.DECORATION,
            classifier.classify(new ItemFacts(null, "oak_sign", true, false, true, 16, false, false, true))
        );
    }

    @Test
    void classify_returnsMiscForHardDisabledAdminItems() {
        final DefaultCategoryClassifier classifier = new DefaultCategoryClassifier();

        assertEquals(
            CatalogCategory.MISC,
            classifier.classify(new ItemFacts(null, "command_block", true, true, false, 64, false, false, false))
        );
    }
}
