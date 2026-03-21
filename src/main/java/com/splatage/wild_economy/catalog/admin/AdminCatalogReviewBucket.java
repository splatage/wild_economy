package com.splatage.wild_economy.catalog.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AdminCatalogReviewBucket(
    String bucketId,
    String description,
    int count,
    List<String> sampleItems,
    Map<String, Integer> subgroupCounts,
    Map<String, List<String>> subgroupSampleItems
) {
    public AdminCatalogReviewBucket {
        sampleItems = sampleItems == null ? List.of() : List.copyOf(sampleItems);
        subgroupCounts = subgroupCounts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(subgroupCounts));
        subgroupSampleItems = normalizeSampleMap(subgroupSampleItems);
    }

    private static Map<String, List<String>> normalizeSampleMap(final Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        final Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(entry.getKey(), entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(normalized);
    }
}

