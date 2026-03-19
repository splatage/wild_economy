package com.splatage.wild_economy.catalog.policy;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.math.BigDecimal;

public interface PolicySuggestionService {
    CatalogPolicy suggest(ItemFacts facts, CatalogCategory category, BigDecimal basePrice);

    String includeReason(ItemFacts facts, CatalogCategory category, BigDecimal basePrice, CatalogPolicy policy);

    String excludeReason(ItemFacts facts, CatalogCategory category, BigDecimal basePrice, CatalogPolicy policy);
}
