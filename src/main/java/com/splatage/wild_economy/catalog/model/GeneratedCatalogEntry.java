package com.splatage.wild_economy.catalog.model;

import java.math.BigDecimal;

public record GeneratedCatalogEntry(
    String key,
    CatalogCategory category,
    CatalogPolicy policy,
    boolean worthPresent,
    BigDecimal basePrice,
    String includeReason,
    String excludeReason,
    String notes
) {
}
