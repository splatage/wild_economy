package com.splatage.wild_economy.catalog.admin;

import java.util.List;

public record AdminCatalogReviewBucket(
    String bucketId,
    String description,
    int count,
    List<String> sampleItems
) {
    public AdminCatalogReviewBucket {
        sampleItems = sampleItems == null ? List.of() : List.copyOf(sampleItems);
    }
}

