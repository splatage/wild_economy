package com.splatage.wild_economy.catalog.admin;

import java.io.File;
import java.util.List;

public record AdminCatalogBuildResult(
    List<AdminCatalogPlanEntry> proposedEntries,
    List<AdminCatalogPlanEntry> liveEntries,
    List<AdminCatalogDecisionTrace> decisionTraces,
    List<AdminCatalogDiffEntry> diffEntries,
    List<AdminCatalogValidationIssue> validationIssues,
    File generatedDirectory,
    File liveCatalogFile,
    File snapshotDirectory,
    int totalScanned,
    int disabledCount,
    int unresolvedCount,
    int warningCount,
    int errorCount
) {
    public AdminCatalogBuildResult {
        proposedEntries = List.copyOf(proposedEntries);
        liveEntries = List.copyOf(liveEntries);
        decisionTraces = List.copyOf(decisionTraces);
        diffEntries = List.copyOf(diffEntries);
        validationIssues = List.copyOf(validationIssues);
    }
}
