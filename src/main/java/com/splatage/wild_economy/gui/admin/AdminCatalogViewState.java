package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogDecisionTrace;
import com.splatage.wild_economy.catalog.admin.AdminCatalogItemKeys;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogReviewBucket;
import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import java.util.List;
import java.util.Objects;

public record AdminCatalogViewState(
    AdminCatalogBuildResult buildResult,
    List<AdminCatalogRuleImpact> ruleImpacts,
    List<AdminCatalogReviewBucket> reviewBuckets,
    String lastAction
) {
    public AdminCatalogViewState {
        buildResult = Objects.requireNonNull(buildResult, "buildResult");
        ruleImpacts = ruleImpacts == null ? List.of() : List.copyOf(ruleImpacts);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
        lastAction = lastAction == null ? "preview" : lastAction;
    }

    public AdminCatalogDecisionTrace findTrace(final String itemKey) {
        final String canonical = AdminCatalogItemKeys.canonicalize(itemKey);
        for (final AdminCatalogDecisionTrace trace : this.buildResult.decisionTraces()) {
            if (canonical.equals(AdminCatalogItemKeys.canonicalize(trace.itemKey()))) {
                return trace;
            }
        }
        return null;
    }

    public AdminCatalogPlanEntry findPlanEntry(final String itemKey) {
        final String canonical = AdminCatalogItemKeys.canonicalize(itemKey);
        for (final AdminCatalogPlanEntry entry : this.buildResult.proposedEntries()) {
            if (canonical.equals(AdminCatalogItemKeys.canonicalize(entry.itemKey()))) {
                return entry;
            }
        }
        return null;
    }

    public AdminCatalogRuleImpact findRuleImpact(final String ruleId) {
        if (ruleId == null) {
            return null;
        }
        for (final AdminCatalogRuleImpact ruleImpact : this.ruleImpacts) {
            if (ruleId.equals(ruleImpact.ruleId())) {
                return ruleImpact;
            }
        }
        return null;
    }

    public AdminCatalogReviewBucket findReviewBucket(final String bucketId) {
        if (bucketId == null) {
            return null;
        }
        for (final AdminCatalogReviewBucket reviewBucket : this.reviewBuckets) {
            if (bucketId.equals(reviewBucket.bucketId())) {
                return reviewBucket;
            }
        }
        return null;
    }
}

