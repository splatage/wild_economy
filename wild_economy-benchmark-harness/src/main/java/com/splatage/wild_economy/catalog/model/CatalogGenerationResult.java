package com.splatage.wild_economy.catalog.model;

import java.util.List;

public record CatalogGenerationResult(
    List<GeneratedCatalogEntry> entries,
    int totalScanned,
    int totalGenerated,
    int totalDisabled,
    int rootAnchoredCount,
    int derivedIncludedCount
) {
}
