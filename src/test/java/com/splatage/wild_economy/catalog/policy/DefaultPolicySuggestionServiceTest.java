package com.splatage.wild_economy.catalog.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

final class DefaultPolicySuggestionServiceTest {

    private static final ItemFacts SAFE_FACTS = new ItemFacts(null, "oak_stairs", true, true, true, 64, false, true, false);
    private static final DerivedItemResult RESOLVED_DERIVATION = new DerivedItemResult(
        true,
        true,
        new BigDecimal("1.00"),
        Integer.valueOf(3),
        new BigDecimal("2.50"),
        DerivationReason.DERIVED_FROM_ROOT
    );

    @Test
    void suggest_keepsHardDisabledAdminItemsOutOfGeneratedCatalog() {
        final DefaultPolicySuggestionService service = new DefaultPolicySuggestionService();
        final ItemFacts facts = new ItemFacts(null, "zombie_spawn_egg", true, false, true, 64, false, false, false);

        assertEquals(
            CatalogPolicy.DISABLED,
            service.suggest(facts, CatalogCategory.MISC, RESOLVED_DERIVATION)
        );
        assertEquals(
            "hard-disabled non-standard or admin item",
            service.excludeReason(facts, CatalogCategory.MISC, RESOLVED_DERIVATION, CatalogPolicy.DISABLED)
        );
    }

    @Test
    void suggest_usesFallbackExchangeForResolvedItemsAndLeavesPruningToConfig() {
        final DefaultPolicySuggestionService service = new DefaultPolicySuggestionService(1);

        assertEquals(
            CatalogPolicy.EXCHANGE,
            service.suggest(SAFE_FACTS, CatalogCategory.MISC, RESOLVED_DERIVATION)
        );
        assertEquals(
            "derived-from-root",
            service.includeReason(SAFE_FACTS, CatalogCategory.MISC, RESOLVED_DERIVATION, CatalogPolicy.EXCHANGE)
        );
    }

    @Test
    void suggest_disablesItemsWithoutResolvedRootedValuePath() {
        final DefaultPolicySuggestionService service = new DefaultPolicySuggestionService();
        final DerivedItemResult unresolved = new DerivedItemResult(
            false,
            false,
            null,
            null,
            null,
            DerivationReason.NO_RECIPE_AND_NO_ROOT
        );

        assertEquals(
            CatalogPolicy.DISABLED,
            service.suggest(SAFE_FACTS, CatalogCategory.MISC, unresolved)
        );
        assertEquals(
            "no-recipe-and-no-root",
            service.excludeReason(SAFE_FACTS, CatalogCategory.MISC, unresolved, CatalogPolicy.DISABLED)
        );
    }
}
