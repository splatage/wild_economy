package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AdminCatalogRuleImpact(
    String ruleId,
    boolean fallbackRule,
    boolean hasMatchCriteria,
    int matchCount,
    int winCount,
    int lossCount,
    Map<CatalogPolicy, Integer> winningPolicies,
    Map<CatalogPolicy, Integer> lostToPolicies,
    Map<String, Integer> lostToRules,
    List<String> sampleMatchedItems,
    List<String> sampleWinningItems,
    List<String> sampleLostItems
) {
    public AdminCatalogRuleImpact {
        winningPolicies = normalizePolicyMap(winningPolicies);
        lostToPolicies = normalizePolicyMap(lostToPolicies);
        lostToRules = lostToRules == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(lostToRules));
        sampleMatchedItems = sampleMatchedItems == null ? List.of() : List.copyOf(sampleMatchedItems);
        sampleWinningItems = sampleWinningItems == null ? List.of() : List.copyOf(sampleWinningItems);
        sampleLostItems = sampleLostItems == null ? List.of() : List.copyOf(sampleLostItems);
    }

    private static Map<CatalogPolicy, Integer> normalizePolicyMap(final Map<CatalogPolicy, Integer> source) {
        final Map<CatalogPolicy, Integer> normalized = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            normalized.put(policy, Integer.valueOf(0));
        }
        if (source != null) {
            for (final Map.Entry<CatalogPolicy, Integer> entry : source.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalized.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Map.copyOf(normalized);
    }
}

