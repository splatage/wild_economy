package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.math.BigDecimal;
import java.util.List;

public record AdminCatalogDecisionTrace(
    String itemKey,
    CatalogCategory classifiedCategory,
    DerivationReason derivationReason,
    Integer derivationDepth,
    boolean rootValuePresent,
    BigDecimal rootValue,
    BigDecimal derivedValue,
    CatalogPolicy baseSuggestedPolicy,
    List<String> matchedRuleIds,
    String winningRuleId,
    boolean manualOverrideApplied,
    CatalogPolicy finalPolicy,
    CatalogCategory finalCategory,
    String stockProfile,
    String ecoEnvelope,
    String postRuleAdjustment,
    String displayName,
    String note
) {
    public AdminCatalogDecisionTrace {
        matchedRuleIds = List.copyOf(matchedRuleIds);
    }
}

