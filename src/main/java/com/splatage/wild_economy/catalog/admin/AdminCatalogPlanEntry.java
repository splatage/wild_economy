package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.math.BigDecimal;

public record AdminCatalogPlanEntry(
    String itemKey,
    String displayName,
    CatalogCategory category,
    CatalogPolicy policy,
    String policyProfileId,
    String runtimePolicy,
    boolean buyEnabled,
    boolean sellEnabled,
    boolean stockBacked,
    boolean unlimitedBuy,
    boolean requiresPlayerStockToBuy,
    int stockCap,
    int turnoverAmountPerInterval,
    BigDecimal anchorValue,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    String stockProfile,
    String ecoEnvelope,
    String derivationReason,
    Integer derivationDepth,
    String includeReason,
    String excludeReason,
    String notes
) { }

