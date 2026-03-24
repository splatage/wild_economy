package com.splatage.wild_economy.catalog.classify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DefaultCategoryClassifierTest {

    @Test
    void classify_prefersLayoutHintBeforeFallbackHeuristics() {
        final ItemFacts facts = new ItemFacts(null, "bone", true, false, true, 64, false, false, true);

        final DefaultCategoryClassifier fallbackClassifier = new DefaultCategoryClassifier();
        assertEquals(CatalogCategory.MOB_DROPS, fallbackClassifier.classify(facts));

        final DefaultCategoryClassifier hintedClassifier = new DefaultCategoryClassifier(
            itemKey -> "bone".equals(itemKey) ? Optional.of(CatalogCategory.REDSTONE) : Optional.empty()
        );
        assertEquals(CatalogCategory.REDSTONE, hintedClassifier.classify(facts));
    }
}
