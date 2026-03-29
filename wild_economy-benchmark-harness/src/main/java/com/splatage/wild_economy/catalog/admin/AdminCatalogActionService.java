package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AdminCatalogActionService {

    private final WildEconomyPlugin plugin;
    private final AdminCatalogPhaseOneService catalogService;

    public AdminCatalogActionService(final WildEconomyPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.catalogService = new AdminCatalogPhaseOneService(plugin);
    }

    public void reload() {
        this.plugin.getBootstrap().reload();
    }

    public AdminCatalogActionResult preview() throws IOException {
        return this.build("preview", false);
    }

    public AdminCatalogActionResult validate() throws IOException {
        return this.build("validate", false);
    }

    public AdminCatalogActionResult diff() throws IOException {
        return this.build("diff", false);
    }

    public AdminCatalogActionResult apply() throws IOException {
        final AdminCatalogActionResult result = this.build("apply", true);
        this.reload();
        return result.withRuntimeReloaded(true);
    }

    public ItemInspectResult inspectItem(final String requestedKey) throws IOException {
        final AdminCatalogBuildResult result = this.catalogService.build(false);
        final String canonicalKey = AdminCatalogItemKeys.canonicalize(requestedKey);

        AdminCatalogDecisionTrace trace = null;
        for (final AdminCatalogDecisionTrace candidate : result.decisionTraces()) {
            if (canonicalKey.equals(AdminCatalogItemKeys.canonicalize(candidate.itemKey()))) {
                trace = candidate;
                break;
            }
        }

        if (trace == null) {
            return ItemInspectResult.notFound(canonicalKey, result);
        }

        AdminCatalogPlanEntry planEntry = null;
        for (final AdminCatalogPlanEntry candidate : result.proposedEntries()) {
            if (canonicalKey.equals(AdminCatalogItemKeys.canonicalize(candidate.itemKey()))) {
                planEntry = candidate;
                break;
            }
        }

        final List<String> reviewBuckets = this.findReviewBuckets(trace, planEntry);
        final List<String> matchedButLost = new ArrayList<>();
        for (final String matchedRuleId : trace.matchedRuleIds()) {
            if (!matchedRuleId.equals(trace.winningRuleId())) {
                matchedButLost.add(matchedRuleId);
            }
        }

        return ItemInspectResult.found(canonicalKey, result, trace, planEntry, reviewBuckets, matchedButLost);
    }

    private AdminCatalogActionResult build(final String actionName, final boolean apply) throws IOException {
        return new AdminCatalogActionResult(actionName, this.catalogService.build(apply), false);
    }

    private List<String> findReviewBuckets(
        final AdminCatalogDecisionTrace trace,
        final AdminCatalogPlanEntry planEntry
    ) {
        final List<String> buckets = new ArrayList<>();
        if (planEntry != null && planEntry.policy() != CatalogPolicy.DISABLED && planEntry.category() == CatalogCategory.MISC) {
            buckets.add("live-misc-items");
        }
        if (trace.derivationReason() == DerivationReason.NO_RECIPE_AND_NO_ROOT) {
            buckets.add("no-root-path");
        }
        if (trace.derivationReason() == DerivationReason.ALL_PATHS_BLOCKED) {
            buckets.add("blocked-paths");
        }
        if (trace.manualOverrideApplied()) {
            buckets.add("manual-overrides");
        }
        if (trace.finalPolicy() == CatalogPolicy.SELL_ONLY) {
            buckets.add("sell-only-review");
        }
        return buckets;
    }

    public record AdminCatalogActionResult(
        String actionName,
        AdminCatalogBuildResult buildResult,
        boolean runtimeReloaded
    ) {
        public AdminCatalogActionResult {
            Objects.requireNonNull(actionName, "actionName");
            Objects.requireNonNull(buildResult, "buildResult");
        }

        public AdminCatalogActionResult withRuntimeReloaded(final boolean runtimeReloaded) {
            return new AdminCatalogActionResult(this.actionName, this.buildResult, runtimeReloaded);
        }
    }

    public record ItemInspectResult(
        String requestedKey,
        AdminCatalogBuildResult buildResult,
        boolean found,
        AdminCatalogDecisionTrace trace,
        AdminCatalogPlanEntry planEntry,
        List<String> reviewBuckets,
        List<String> matchedButLost
    ) {
        public ItemInspectResult {
            Objects.requireNonNull(requestedKey, "requestedKey");
            Objects.requireNonNull(buildResult, "buildResult");
            reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
            matchedButLost = matchedButLost == null ? List.of() : List.copyOf(matchedButLost);
        }

        public static ItemInspectResult notFound(
            final String requestedKey,
            final AdminCatalogBuildResult buildResult
        ) {
            return new ItemInspectResult(requestedKey, buildResult, false, null, null, List.of(), List.of());
        }

        public static ItemInspectResult found(
            final String requestedKey,
            final AdminCatalogBuildResult buildResult,
            final AdminCatalogDecisionTrace trace,
            final AdminCatalogPlanEntry planEntry,
            final List<String> reviewBuckets,
            final List<String> matchedButLost
        ) {
            return new ItemInspectResult(
                requestedKey,
                buildResult,
                true,
                trace,
                planEntry,
                reviewBuckets,
                matchedButLost
            );
        }
    }
}

