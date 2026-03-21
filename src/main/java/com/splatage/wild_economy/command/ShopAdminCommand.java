package com.splatage.wild_economy.command;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogDecisionTrace;
import com.splatage.wild_economy.catalog.admin.AdminCatalogDiffEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogItemKeys;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPhaseOneService;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogValidationIssue;
import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopAdminCommand implements CommandExecutor {

    private final WildEconomyPlugin plugin;
    private final AdminCatalogPhaseOneService catalogService;

    public ShopAdminCommand(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
        this.catalogService = new AdminCatalogPhaseOneService(plugin);
    }

    @Override
    public boolean onCommand(
        final CommandSender sender,
        final Command command,
        final String label,
        final String[] args
    ) {
        if (args.length == 0) {
            this.sendUsage(sender);
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "reload" -> this.handleReload(sender);
            case "generatecatalog" -> this.handleCatalogPreview(sender);
            case "catalog" -> this.handleCatalog(sender, args);
            case "item" -> this.handleItemInspect(sender, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown admin subcommand.");
                this.sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleCatalog(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin catalog <preview|validate|diff|apply>.");
            return true;
        }

        final String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "preview" -> this.handleCatalogPreview(sender);
            case "validate" -> this.handleCatalogValidate(sender);
            case "diff" -> this.handleCatalogDiff(sender);
            case "apply" -> this.handleCatalogApply(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown catalog action.");
                sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin catalog <preview|validate|diff|apply>.");
                yield true;
            }
        };
    }

    private boolean handleReload(final CommandSender sender) {
        try {
            this.plugin.reloadConfig();
            this.plugin.getBootstrap().reload();
            sender.sendMessage(ChatColor.GREEN + "wild_economy reloaded.");
        } catch (final RuntimeException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to reload wild_economy", exception);
            sender.sendMessage(ChatColor.RED + "Reload failed. Check console for details.");
        }
        return true;
    }

    private boolean handleCatalogPreview(final CommandSender sender) {
        return this.runCatalogAction(sender, "preview", false, true);
    }

    private boolean handleCatalogValidate(final CommandSender sender) {
        return this.runCatalogAction(sender, "validate", false, false);
    }

    private boolean handleCatalogDiff(final CommandSender sender) {
        return this.runCatalogAction(sender, "diff", false, false);
    }

    private boolean handleCatalogApply(final CommandSender sender) {
        return this.runCatalogAction(sender, "apply", true, false);
    }

    private boolean handleItemInspect(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin item <item_key>.");
            return true;
        }

        try {
            final AdminCatalogBuildResult result = this.catalogService.build(false);
            final String requestedKey = AdminCatalogItemKeys.canonicalize(args[1]);
            AdminCatalogDecisionTrace trace = null;
            for (final AdminCatalogDecisionTrace candidate : result.decisionTraces()) {
                if (requestedKey.equals(AdminCatalogItemKeys.canonicalize(candidate.itemKey()))) {
                    trace = candidate;
                    break;
                }
            }

            if (trace == null) {
                sender.sendMessage(ChatColor.RED + "No generated catalog decision found for '" + requestedKey + "'.");
                sender.sendMessage(ChatColor.YELLOW + "Run /shopadmin catalog preview and confirm the item key.");
                return true;
            }

            AdminCatalogPlanEntry planEntry = null;
            for (final AdminCatalogPlanEntry candidate : result.proposedEntries()) {
                if (requestedKey.equals(AdminCatalogItemKeys.canonicalize(candidate.itemKey()))) {
                    planEntry = candidate;
                    break;
                }
            }

            sender.sendMessage(ChatColor.GOLD + "Item inspector: " + trace.displayName() + ChatColor.GRAY + " (" + trace.itemKey() + ")");
            sender.sendMessage(
                ChatColor.YELLOW
                    + "Category "
                    + trace.classifiedCategory().name()
                    + " -> "
                    + trace.finalCategory().name()
                    + ", derivation "
                    + trace.derivationReason().name()
                    + ", depth "
                    + String.valueOf(trace.derivationDepth())
                    + "."
            );
            sender.sendMessage(
                ChatColor.AQUA
                    + "Policy "
                    + trace.baseSuggestedPolicy().name()
                    + " -> "
                    + trace.finalPolicy().name()
                    + ", stock-profile "
                    + trace.stockProfile()
                    + ", eco-envelope "
                    + trace.ecoEnvelope()
                    + "."
            );
            if (planEntry != null) {
                sender.sendMessage(
                    ChatColor.AQUA
                        + "Runtime "
                        + planEntry.runtimePolicy()
                        + ", buy="
                        + planEntry.buyEnabled()
                        + ", sell="
                        + planEntry.sellEnabled()
                        + ", buy-price="
                        + String.valueOf(planEntry.buyPrice())
                        + ", sell-price="
                        + String.valueOf(planEntry.sellPrice())
                        + "."
                );
            }
            sender.sendMessage(
                ChatColor.GRAY
                    + "Winning rule: "
                    + String.valueOf(trace.winningRuleId())
                    + ", matched rules: "
                    + trace.matchedRuleIds()
                    + ", manual override="
                    + trace.manualOverrideApplied()
                    + "."
            );
            if (trace.postRuleAdjustment() != null && !trace.postRuleAdjustment().isBlank()) {
                sender.sendMessage(ChatColor.YELLOW + "Adjustment: " + trace.postRuleAdjustment());
            }
            if (trace.note() != null && !trace.note().isBlank()) {
                sender.sendMessage(ChatColor.GRAY + "Notes: " + trace.note());
            }
            sender.sendMessage(
                ChatColor.GREEN
                    + "Detailed traces are also written to "
                    + result.generatedDirectory().getPath()
                    + "/item-decision-traces.yml."
            );
            return true;
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to inspect generated catalog item", exception);
            sender.sendMessage(ChatColor.RED + "Item inspect failed: " + exception.getMessage());
            return true;
        }
    }

    private boolean runCatalogAction(
        final CommandSender sender,
        final String actionName,
        final boolean apply,
        final boolean includeTopItems
    ) {
        try {
            final AdminCatalogBuildResult result = this.catalogService.build(apply);

            this.sendSummary(sender, result, actionName);
            this.sendValidationSummary(sender, result);
            this.sendPolicySummary(sender, result);
            this.sendReviewSummary(sender, result);
            this.sendDiffSummary(sender, result, includeTopItems);

            if (apply) {
                sender.sendMessage(ChatColor.GREEN + "Published catalog written to " + result.liveCatalogFile().getPath());
                sender.sendMessage(ChatColor.GREEN + "Generated reports written to " + result.generatedDirectory().getPath());
                if (result.snapshotDirectory() != null) {
                    sender.sendMessage(ChatColor.GREEN + "Snapshot created at " + result.snapshotDirectory().getPath());
                }
                sender.sendMessage(ChatColor.YELLOW + "Reloading plugin to apply the new live catalog...");
                this.plugin.reloadConfig();
                this.plugin.getBootstrap().reload();
                sender.sendMessage(ChatColor.GREEN + "wild_economy reloaded with the published catalog.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Generated reports written to " + result.generatedDirectory().getPath());
            }
            sender.sendMessage(
                ChatColor.GREEN
                    + "Additional review reports: generated/generated-rule-impacts.yml and generated/generated-review-buckets.yml."
            );
            return true;
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to " + actionName + " catalog", exception);
            sender.sendMessage(ChatColor.RED + "Catalog " + actionName + " failed: " + exception.getMessage());
            return true;
        }
    }

    private void sendSummary(final CommandSender sender, final AdminCatalogBuildResult result, final String actionName) {
        sender.sendMessage(ChatColor.GOLD + "Catalog " + actionName + " complete.");
        sender.sendMessage(
            ChatColor.YELLOW
                + "Scanned "
                + result.totalScanned()
                + " items, proposed "
                + result.proposedEntries().size()
                + " entries, live-enabled "
                + result.liveEntries().size()
                + " entries."
        );
        sender.sendMessage(
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
    }

    private void sendValidationSummary(final CommandSender sender, final AdminCatalogBuildResult result) {
        if (result.validationIssues().isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Validation passed with no issues.");
            return;
        }

        for (final AdminCatalogValidationIssue issue : result.validationIssues().stream().limit(6).toList()) {
            final ChatColor color = issue.severity() == AdminCatalogValidationIssue.Severity.ERROR
                ? ChatColor.RED
                : ChatColor.YELLOW;
            sender.sendMessage(color + "[" + issue.severity().name() + "] " + issue.message());
        }

        if (result.validationIssues().size() > 6) {
            sender.sendMessage(
                ChatColor.YELLOW
                    + "Additional validation issues were written to generated/generated-validation.yml."
            );
        }
    }

    private void sendPolicySummary(final CommandSender sender, final AdminCatalogBuildResult result) {
        final Map<CatalogPolicy, Integer> counts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            counts.put(policy, 0);
        }
        result.proposedEntries().forEach(entry -> counts.compute(entry.policy(), (ignored, value) -> value + 1));

        sender.sendMessage(
            ChatColor.AQUA
                + "Policies: ALWAYS_AVAILABLE="
                + counts.get(CatalogPolicy.ALWAYS_AVAILABLE)
                + ", EXCHANGE="
                + counts.get(CatalogPolicy.EXCHANGE)
                + ", SELL_ONLY="
                + counts.get(CatalogPolicy.SELL_ONLY)
                + ", DISABLED="
                + counts.get(CatalogPolicy.DISABLED)
        );
    }

    private void sendReviewSummary(final CommandSender sender, final AdminCatalogBuildResult result) {
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

        sender.sendMessage(
            ChatColor.AQUA
                + "Review buckets: live-MISC="
                + liveMiscCount
                + ", no-root-path="
                + noRootPathCount
                + ", blocked-paths="
                + blockedPathCount
                + ", manual-overrides="
                + manualOverrideCount
                + "."
        );
    }

    private void sendDiffSummary(
        final CommandSender sender,
        final AdminCatalogBuildResult result,
        final boolean includeTopItems
    ) {
        if (result.diffEntries().isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No live catalog differences detected.");
            return;
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

        sender.sendMessage(
            ChatColor.AQUA
                + "Diff: added "
                + added
                + ", removed "
                + removed
                + ", changed "
                + changed
                + "."
        );

        if (includeTopItems) {
            for (final AdminCatalogDiffEntry entry : result.diffEntries().stream().limit(5).toList()) {
                sender.sendMessage(ChatColor.GRAY + "- " + entry.itemKey() + ": " + entry.summary());
            }
        }
    }

    private void sendUsage(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin reload");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin catalog <preview|validate|diff|apply>");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin item <item_key>");
    }
}

