package com.splatage.wild_economy.catalog.classify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import org.junit.jupiter.api.Test;

final class DefaultCategoryClassifierTest {

    @Test
    void classify_usesFallbackHeuristicsWithoutLayoutHints() {
        final DefaultCategoryClassifier classifier = new DefaultCategoryClassifier();

        assertEquals(
            CatalogCategory.MOB_DROPS,
            classifier.classify(new ItemFacts(null, "bone", true, false, true, 64, false, false, true))
        );
        assertEquals(
            CatalogCategory.WOODS,
            classifier.classify(new ItemFacts(null, "oak_log", true, true, true, 64, true, false, false))
        );
    }
}
