package com.splatage.wild_economy.catalog.policy;

import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;

public interface PolicySuggestionService {
    CatalogPolicy suggest(ItemFacts facts, CatalogCategory category, DerivedItemResult derivation);

    String includeReason(ItemFacts facts, CatalogCategory category, DerivedItemResult derivation, CatalogPolicy policy);

    String excludeReason(ItemFacts facts, CatalogCategory category, DerivedItemResult derivation, CatalogPolicy policy);
}
