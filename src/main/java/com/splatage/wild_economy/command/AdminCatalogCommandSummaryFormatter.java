package com.splatage.wild_economy.command;

import com.splatage.wild_economy.catalog.admin.AdminCatalogActionService;
import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogDecisionTrace;
import com.splatage.wild_economy.catalog.admin.AdminCatalogDiffEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogValidationIssue;
import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;

public final class AdminCatalogCommandSummaryFormatter {

    public List<String> formatCatalogAction(
        final AdminCatalogActionService.AdminCatalogActionResult result,
        final boolean includeTopItems
    ) {
        final List<String> lines = new ArrayList<>();
        final AdminCatalogBuildResult buildResult = result.buildResult();

        lines.addAll(this.formatSummary(buildResult, result.actionName()));
        lines.addAll(this.formatValidationSummary(buildResult));
        lines.add(this.formatPolicySummary(buildResult));
        lines.add(this.formatReviewSummary(buildResult));
        lines.addAll(this.formatDiffSummary(buildResult, includeTopItems));

        if (result.actionName().equals("apply")) {
            lines.add(ChatColor.GREEN + "Published catalog written to " + buildResult.liveCatalogFile().getPath());
            lines.add(ChatColor.GREEN + "Generated reports written to " + buildResult.generatedDirectory().getPath());
            if (buildResult.snapshotDirectory() != null) {
                lines.add(ChatColor.GREEN + "Snapshot created at " + buildResult.snapshotDirectory().getPath());
            }
            lines.add(ChatColor.YELLOW + "Reloading plugin to apply the new live catalog...");
            if (result.runtimeReloaded()) {
                lines.add(ChatColor.GREEN + "wild_economy reloaded with the published catalog.");
            }
        } else {
            lines.add(ChatColor.GREEN + "Generated reports written to " + buildResult.generatedDirectory().getPath());
        }

        lines.add(
            ChatColor.GREEN
                + "Additional review reports: generated/generated-rule-impacts.yml and generated/generated-review-buckets.yml."
        );
        return lines;
    }

    public List<String> formatItemInspect(final AdminCatalogActionService.ItemInspectResult result) {
        final List<String> lines = new ArrayList<>();
        if (!result.found()) {
            lines.add(ChatColor.RED + "No generated catalog decision found for '" + result.requestedKey() + "'.");
            lines.add(ChatColor.YELLOW + "Run /shopadmin catalog preview and confirm the item key.");
            return lines;
        }

        final AdminCatalogDecisionTrace trace = result.trace();
        final AdminCatalogPlanEntry planEntry = result.planEntry();

        lines.add(ChatColor.GOLD + "Item inspector: " + trace.displayName() + ChatColor.GRAY + " (" + trace.itemKey() + ")");
        lines.add(this.formatCategoryLine(trace));
        lines.add(
            ChatColor.YELLOW + "Derivation: " + trace.derivationReason().name() + ", depth " + trace.derivationDepth() + "."
        );
        lines.add(ChatColor.AQUA + "Base suggested policy: " + trace.baseSuggestedPolicy().name());
        lines.add(
            ChatColor.AQUA
                + "Final policy: "
                + trace.finalPolicy().name()
                + ", stock-profile "
                + trace.stockProfile()
                + ", eco-envelope "
                + trace.ecoEnvelope()
                + "."
        );

        if (planEntry != null) {
            lines.add(
                ChatColor.AQUA
                    + "Runtime: "
                    + planEntry.runtimePolicy()
                    + ", policy-profile="
                    + planEntry.policyProfileId()
                    + ", base-worth="
                    + planEntry.baseWorth()
                    + "."
            );
            lines.add(
                ChatColor.AQUA
                    + "Eco: min-stock="
                    + planEntry.ecoMinStockInclusive()
                    + ", max-stock="
                    + planEntry.ecoMaxStockInclusive()
                    + ", buy[min/max]="
                    + planEntry.buyPriceAtMinStock()
                    + "/"
                    + planEntry.buyPriceAtMaxStock()
                    + ", sell[min/max]="
                    + planEntry.sellPriceAtMinStock()
                    + "/"
                    + planEntry.sellPriceAtMaxStock()
                    + "."
            );
            lines.add(
                ChatColor.AQUA
                    + "Effective behavior: buyable="
                    + planEntry.buyEnabled()
                    + ", sellable="
                    + planEntry.sellEnabled()
                    + ", stock-backed="
                    + planEntry.stockBacked()
                    + ", unlimited-buy="
                    + planEntry.unlimitedBuy()
                    + ", requires-player-stock="
                    + planEntry.requiresPlayerStockToBuy()
                    + "."
            );
        }

        lines.add(ChatColor.GRAY + this.buildDecisionSourceLine(trace, result.matchedButLost()));
        if (!result.matchedButLost().isEmpty()) {
            lines.add(ChatColor.GRAY + "Matched but lost: " + result.matchedButLost() + ".");
        }

        lines.add(ChatColor.GRAY + "Manual override: " + (trace.manualOverrideApplied() ? "yes" : "no") + ".");
        if (!result.reviewBuckets().isEmpty()) {
            lines.add(ChatColor.GREEN + "Review buckets: " + result.reviewBuckets() + ".");
        }

        if (trace.postRuleAdjustment() != null && !trace.postRuleAdjustment().isBlank()) {
            lines.add(ChatColor.YELLOW + "Adjustment: " + trace.postRuleAdjustment());
        }

        if (trace.note() != null && !trace.note().isBlank()) {
            lines.add(ChatColor.GRAY + "Notes: " + trace.note());
        }

        lines.add(
            ChatColor.GREEN
                + "Detailed traces are also written to "
                + result.buildResult().generatedDirectory().getPath()
                + "/item-decision-traces.yml."
        );
        return lines;
    }

    private List<String> formatSummary(final AdminCatalogBuildResult result, final String actionName) {
        final List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "Catalog " + actionName + " complete.");
        lines.add(
            ChatColor.YELLOW
                + "Scanned "
                + result.totalScanned()
                + " items, proposed "
                + result.proposedEntries().size()
                + " entries, live-enabled "
                + result.liveEntries().size()
                + " entries."
        );
        lines.add(
            ChatColor.YELLOW
                + "Disabled "
                + result.disabledCount()
                + ", unresolved "
                + result.unresolvedCount()
                + ", warnings "
                + result.warningCount()
                + ", errors "
                + result.errorCount()
                + "."
        );
        return lines;
    }

    private List<String> formatValidationSummary(final AdminCatalogBuildResult result) {
        final List<String> lines = new ArrayList<>();
        if (result.validationIssues().isEmpty()) {
            lines.add(ChatColor.GREEN + "Validation passed with no issues.");
            return lines;
        }

        for (final AdminCatalogValidationIssue issue : result.validationIssues().stream().limit(6).toList()) {
            final ChatColor color =
                issue.severity() == AdminCatalogValidationIssue.Severity.ERROR ? ChatColor.RED : ChatColor.YELLOW;
            lines.add(color + "[" + issue.severity().name() + "] " + issue.message());
        }

        if (result.validationIssues().size() > 6) {
            lines.add(ChatColor.YELLOW + "Additional validation issues were written to generated/generated-validation.yml.");
        }
        return lines;
    }

    private String formatPolicySummary(final AdminCatalogBuildResult result) {
        final Map<CatalogPolicy, Integer> counts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            counts.put(policy, 0);
        }
        result.proposedEntries().forEach(entry -> counts.compute(entry.policy(), (ignored, value) -> value + 1));
        return ChatColor.AQUA
            + "Policies: ALWAYS_AVAILABLE="
            + counts.get(CatalogPolicy.ALWAYS_AVAILABLE)
            + ", EXCHANGE="
            + counts.get(CatalogPolicy.EXCHANGE)
            + ", SELL_ONLY="
            + counts.get(CatalogPolicy.SELL_ONLY)
            + ", DISABLED="
            + counts.get(CatalogPolicy.DISABLED);
    }

    private String formatReviewSummary(final AdminCatalogBuildResult result) {
        int liveMiscCount = 0;
        int noRootPathCount = 0;
        int blockedPathCount = 0;
        int manualOverrideCount = 0;

        for (final AdminCatalogPlanEntry entry : result.proposedEntries()) {
            if (entry.policy() != CatalogPolicy.DISABLED && entry.category() == CatalogCategory.MISC) {
                liveMiscCount++;
            }
        }

        for (final AdminCatalogDecisionTrace trace : result.decisionTraces()) {
            if (trace.derivationReason() == DerivationReason.NO_RECIPE_AND_NO_ROOT) {
                noRootPathCount++;
            }
            if (trace.derivationReason() == DerivationReason.ALL_PATHS_BLOCKED) {
                blockedPathCount++;
            }
            if (trace.manualOverrideApplied()) {
                manualOverrideCount++;
            }
        }

        return ChatColor.AQUA
            + "Review buckets: live-MISC="
            + liveMiscCount
            + ", no-root-path="
            + noRootPathCount
            + ", blocked-paths="
            + blockedPathCount
            + ", manual-overrides="
            + manualOverrideCount
            + ".";
    }

    private List<String> formatDiffSummary(
        final AdminCatalogBuildResult result,
        final boolean includeTopItems
    ) {
        final List<String> lines = new ArrayList<>();
        if (result.diffEntries().isEmpty()) {
            lines.add(ChatColor.GREEN + "No live catalog differences detected.");
            return lines;
        }

        int added = 0;
        int removed = 0;
        int changed = 0;
        for (final AdminCatalogDiffEntry entry : result.diffEntries()) {
            switch (entry.changeType()) {
                case ADDED -> added++;
                case REMOVED -> removed++;
                case CHANGED -> changed++;
            }
        }

        lines.add(ChatColor.AQUA + "Diff: added " + added + ", removed " + removed + ", changed " + changed + ".");
        if (includeTopItems) {
            for (final AdminCatalogDiffEntry entry : result.diffEntries().stream().limit(5).toList()) {
                lines.add(ChatColor.GRAY + "- " + entry.itemKey() + ": " + entry.summary());
            }
        }
        return lines;
    }

    private String formatCategoryLine(final AdminCatalogDecisionTrace trace) {
        if (trace.classifiedCategory() == trace.finalCategory()) {
            return ChatColor.YELLOW + "Category: " + trace.finalCategory().name() + ".";
        }
        return ChatColor.YELLOW + "Category: " + trace.classifiedCategory().name() + " -> " + trace.finalCategory().name() + ".";
    }

    private String buildDecisionSourceLine(
        final AdminCatalogDecisionTrace trace,
        final List<String> matchedButLost
    ) {
        final String winningRuleId = trace.winningRuleId();
        if (winningRuleId == null || winningRuleId.isBlank()) {
            return "Decision source: none recorded.";
        }
        if (trace.matchedRuleIds().isEmpty()) {
            return "Decision source: fallback rule " + winningRuleId + ".";
        }
        return "Decision source: rule " + winningRuleId + ", matched specific rules=" + trace.matchedRuleIds() + ".";
    }
}

