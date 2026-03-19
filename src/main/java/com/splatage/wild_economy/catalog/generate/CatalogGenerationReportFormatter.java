package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;

public final class CatalogGenerationReportFormatter {

    private CatalogGenerationReportFormatter() {
    }

    public static String formatSingleLine(final CatalogGenerationResult result) {
        return "generated=" + result.totalGenerated()
            + ", scanned=" + result.totalScanned()
            + ", disabled=" + result.totalDisabled()
            + ", rootAnchored=" + result.rootAnchoredCount()
            + ", derivedIncluded=" + result.derivedIncludedCount();
    }
}
