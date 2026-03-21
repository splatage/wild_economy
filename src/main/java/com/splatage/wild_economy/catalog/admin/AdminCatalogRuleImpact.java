package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record AdminCatalogRuleImpact(
    String ruleId,
    boolean fallbackRule,
    boolean hasMatchCriteria,
    int matchCount,
    int winCount,
    Map<CatalogPolicy, Integer> winningPolicies,
    List<String> sampleMatchedItems,
    List<String> sampleWinningItems
) {
    public AdminCatalogRuleImpact {
        final Map<CatalogPolicy, Integer> normalizedWinningPolicies = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            normalizedWinningPolicies.put(policy, Integer.valueOf(0));
        }
        if (winningPolicies != null) {
            for (final Map.Entry<CatalogPolicy, Integer> entry : winningPolicies.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalizedWinningPolicies.put(entry.getKey(), entry.getValue());
                }
            }
        }
        winningPolicies = Map.copyOf(normalizedWinningPolicies);
        sampleMatchedItems = sampleMatchedItems == null ? List.of() : List.copyOf(sampleMatchedItems);
        sampleWinningItems = sampleWinningItems == null ? List.of() : List.copyOf(sampleWinningItems);
    }
}

