package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;

public record AdminCatalogManualOverride(
    String itemKey,
    CatalogPolicy policy,
    CatalogCategory category,
    String stockProfile,
    String ecoEnvelope,
    String note
) { }
