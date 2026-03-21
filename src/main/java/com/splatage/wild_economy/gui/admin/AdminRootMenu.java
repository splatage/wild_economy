package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogReviewBucket;
import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminRootMenu {

    private static final int[] TOP_BUCKET_SLOTS = {18, 19, 20};
    private static final int[] TOP_RULE_SLOTS = {24, 25, 26};
    private static final int[] ISSUE_BUCKET_SLOTS = {36, 37, 38, 39};
    private static final String APPLY_CONFIRM_ACTION = "apply-confirm";

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void open(final Player player, final AdminCatalogViewState state) {
        final AdminMenuHolder holder = AdminMenuHolder.root(state);
        final Inventory inventory = holder.createInventory(54, "Shop Admin");
        final boolean applyConfirm = this.isApplyConfirmState(state);

        inventory.setItem(10, this.summaryItem(state.buildResult(), state.lastAction(), state));
        inventory.setItem(12, this.policyItem(state.buildResult()));
        inventory.setItem(14, this.reviewItem(state));
        inventory.setItem(16, this.issueItem(state));

        inventory.setItem(28, this.actionButton(Material.LIME_STAINED_GLASS_PANE, "Preview"));
        inventory.setItem(29, this.actionButton(Material.YELLOW_STAINED_GLASS_PANE, "Validate"));
        inventory.setItem(30, this.actionButton(Material.PAPER, "Diff"));
        inventory.setItem(32, applyConfirm ? this.confirmApplyButton() : this.actionButton(Material.EMERALD_BLOCK, "Apply"));
        if (applyConfirm) {
            inventory.setItem(33, this.cancelApplyButton());
        }
        inventory.setItem(34, this.actionButton(Material.CHEST, "Review Buckets"));
        inventory.setItem(35, this.actionButton(Material.COMPARATOR, "Rule Impacts"));

        final List<AdminCatalogReviewBucket> topBuckets = this.sortedBuckets(state).stream().limit(TOP_BUCKET_SLOTS.length).toList();
        for (int i = 0; i < topBuckets.size(); i++) {
            inventory.setItem(TOP_BUCKET_SLOTS[i], this.topBucketItem(topBuckets.get(i)));
        }
        final List<AdminCatalogRuleImpact> topRules = this.sortedRuleImpacts(state).stream().limit(TOP_RULE_SLOTS.length).toList();
        for (int i = 0; i < topRules.size(); i++) {
            inventory.setItem(TOP_RULE_SLOTS[i], this.topRuleItem(topRules.get(i)));
        }

        final List<AdminCatalogReviewBucket> issueBuckets = this.issueBuckets(state);
        for (int i = 0; i < Math.min(ISSUE_BUCKET_SLOTS.length, issueBuckets.size()); i++) {
            inventory.setItem(ISSUE_BUCKET_SLOTS[i], this.issueBucketShortcut(issueBuckets.get(i)));
        }

        inventory.setItem(45, this.button(Material.ARROW, "Refresh"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.quickStartItem(state));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getRawSlot();
        final boolean applyConfirm = this.isApplyConfirmState(holder.state());
        switch (slot) {
            case 28 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false);
            case 29 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "validate", false);
            case 30 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "diff", false);
            case 32 -> this.adminMenuRouter.rebuildAndOpenRoot(player, applyConfirm ? "apply" : APPLY_CONFIRM_ACTION, applyConfirm);
            case 33 -> { if (applyConfirm) this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false); }
            case 34 -> this.adminMenuRouter.openReviewBucketList(player, holder.state(), 0, "count");
            case 35 -> this.adminMenuRouter.openRuleImpactList(player, holder.state(), 0, "loss");
            case 45 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false);
            case 49 -> player.closeInventory();
            case 53 -> this.openQuickStart(player, holder.state());
            default -> {
                for (int i = 0; i < TOP_BUCKET_SLOTS.length; i++) {
                    if (slot == TOP_BUCKET_SLOTS[i]) {
                        final List<AdminCatalogReviewBucket> topBuckets = this.sortedBuckets(holder.state());
                        if (i < topBuckets.size()) {
                            this.adminMenuRouter.openReviewBucketDetail(player, holder.state(), topBuckets.get(i).bucketId(), 0, "count");
                        }
                        return;
                    }
                }
                for (int i = 0; i < TOP_RULE_SLOTS.length; i++) {
                    if (slot == TOP_RULE_SLOTS[i]) {
                        final List<AdminCatalogRuleImpact> topRules = this.sortedRuleImpacts(holder.state());
                        if (i < topRules.size()) {
                            this.adminMenuRouter.openRuleImpactDetail(player, holder.state(), topRules.get(i).ruleId(), 0, "loss");
                        }
                        return;
                    }
                }
                for (int i = 0; i < ISSUE_BUCKET_SLOTS.length; i++) {
                    if (slot == ISSUE_BUCKET_SLOTS[i]) {
                        final List<AdminCatalogReviewBucket> issueBuckets = this.issueBuckets(holder.state());
                        if (i < issueBuckets.size()) {
                            this.adminMenuRouter.openReviewBucketDetail(player, holder.state(), issueBuckets.get(i).bucketId(), 0, "count");
                        }
                        return;
                    }
                }
            }
        }
    }

    private void openQuickStart(final Player player, final AdminCatalogViewState state) {
        final AdminCatalogReviewBucket misc = state.findReviewBucket("live-misc-items");
        if (misc != null) {
            this.adminMenuRouter.openReviewBucketDetail(player, state, misc.bucketId(), 0, "count");
            return;
        }
        final List<AdminCatalogReviewBucket> buckets = this.sortedBuckets(state);
        if (!buckets.isEmpty()) {
            this.adminMenuRouter.openReviewBucketDetail(player, state, buckets.get(0).bucketId(), 0, "count");
            return;
        }
        this.adminMenuRouter.openReviewBucketList(player, state, 0, "count");
    }

    private ItemStack summaryItem(final AdminCatalogBuildResult result, final String lastAction, final AdminCatalogViewState state) {
        final List<String> lore = new ArrayList<>();
        lore.add("Last action: " + this.displayAction(lastAction));
        lore.add("Scanned: " + result.totalScanned());
        lore.add("Proposed: " + result.proposedEntries().size());
        lore.add("Live-enabled: " + result.liveEntries().size());
        lore.add("Disabled: " + result.disabledCount());
        lore.add("Unresolved: " + result.unresolvedCount());
        lore.add("Warnings: " + result.warningCount());
        lore.add("Errors: " + result.errorCount());
        final AdminCatalogReviewBucket topBucket = this.sortedBuckets(state).stream().findFirst().orElse(null);
        if (topBucket != null) {
            lore.add("Top review bucket: " + this.pretty(topBucket.bucketId()) + " (" + topBucket.count() + ")");
        }
        if (this.isApplyConfirmAction(lastAction)) {
            lore.add("Apply confirmation active.");
            lore.add("Review this state, then click Confirm Apply.");
        }
        return this.item(Material.BOOK, "Catalog Summary", lore);
    }

    private ItemStack policyItem(final AdminCatalogBuildResult result) {
        final Map<CatalogPolicy, Integer> counts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            counts.put(policy, Integer.valueOf(0));
        }
        for (final AdminCatalogPlanEntry entry : result.proposedEntries()) {
            counts.compute(entry.policy(), (ignored, value) -> Integer.valueOf(value.intValue() + 1));
        }
        return this.item(Material.WRITABLE_BOOK, "Policy Split", List.of(
            "ALWAYS_AVAILABLE: " + counts.get(CatalogPolicy.ALWAYS_AVAILABLE),
            "EXCHANGE: " + counts.get(CatalogPolicy.EXCHANGE),
            "SELL_ONLY: " + counts.get(CatalogPolicy.SELL_ONLY),
            "DISABLED: " + counts.get(CatalogPolicy.DISABLED)
        ));
    }

    private ItemStack reviewItem(final AdminCatalogViewState state) {
        final List<String> lore = new ArrayList<>();
        lore.add("Review buckets: " + state.reviewBuckets().size());
        lore.add("Rule impacts: " + state.ruleImpacts().size());
        for (final AdminCatalogReviewBucket bucket : this.sortedBuckets(state).stream().limit(3).toList()) {
            lore.add(this.pretty(bucket.bucketId()) + ": " + bucket.count());
        }
        lore.add("Use shortcuts below to drill in.");
        return this.item(Material.ENDER_CHEST, "Review Data", lore);
    }

    private ItemStack issueItem(final AdminCatalogViewState state) {
        final List<String> lore = new ArrayList<>();
        final List<AdminCatalogReviewBucket> issues = this.issueBuckets(state);
        if (issues.isEmpty()) {
            lore.add("No issue buckets currently available.");
        } else {
            for (final AdminCatalogReviewBucket bucket : issues.stream().limit(4).toList()) {
                lore.add(this.pretty(bucket.bucketId()) + ": " + bucket.count());
            }
            lore.add("Use bottom-row shortcuts to jump in.");
        }
        final List<AdminCatalogRuleImpact> topRules = this.sortedRuleImpacts(state).stream().limit(2).toList();
        for (final AdminCatalogRuleImpact rule : topRules) {
            lore.add("Rule pressure: " + rule.ruleId() + " (L" + rule.lossCount() + "/W" + rule.winCount() + ")");
        }
        return this.item(Material.REDSTONE_TORCH, "Top Issues", lore);
    }

    private ItemStack topBucketItem(final AdminCatalogReviewBucket bucket) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Count: " + bucket.count());
        for (final Map.Entry<String, Integer> entry : bucket.subgroupCounts().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(3).toList()) {
            lore.add(entry.getKey() + ": " + entry.getValue());
        }
        lore.add("Click to open bucket detail.");
        return this.item(Material.CHEST, this.pretty(bucket.bucketId()), lore);
    }

    private ItemStack topRuleItem(final AdminCatalogRuleImpact ruleImpact) {
        final List<String> lore = new ArrayList<>();
        lore.add("Wins: " + ruleImpact.winCount());
        lore.add("Losses: " + ruleImpact.lossCount());
        if (!ruleImpact.lostToRules().isEmpty()) {
            final Map.Entry<String, Integer> topLoss = ruleImpact.lostToRules().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).findFirst().orElse(null);
            if (topLoss != null) {
                lore.add("Top loss: " + topLoss.getKey() + " (" + topLoss.getValue() + ")");
            }
        }
        lore.add(ruleImpact.fallbackRule() ? "Fallback rule" : "Specific rule");
        lore.add("Click to open rule detail.");
        return this.item(Material.COMPARATOR, ruleImpact.ruleId(), lore);
    }

    private ItemStack issueBucketShortcut(final AdminCatalogReviewBucket bucket) {
        final List<String> lore = new ArrayList<>();
        lore.add("Count: " + bucket.count());
        final Map.Entry<String, Integer> topSubgroup = bucket.subgroupCounts().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).findFirst().orElse(null);
        if (topSubgroup != null) {
            lore.add("Top subgroup: " + topSubgroup.getKey() + " (" + topSubgroup.getValue() + ")");
        }
        lore.add("Click to open bucket detail.");
        return this.item(Material.HOPPER, this.pretty(bucket.bucketId()), lore);
    }

    private ItemStack quickStartItem(final AdminCatalogViewState state) {
        final AdminCatalogReviewBucket misc = state.findReviewBucket("live-misc-items");
        final List<String> lore = new ArrayList<>();
        if (misc != null) {
            lore.add("Quick start: live misc items (" + misc.count() + ")");
            lore.add("Use this to review the most likely reclassification targets.");
        } else {
            lore.add("Quick start: top review bucket.");
            lore.add("Opens the biggest current review bucket.");
        }
        return this.item(Material.COMPASS, "Jump to Top Issue", lore);
    }

    private List<AdminCatalogReviewBucket> sortedBuckets(final AdminCatalogViewState state) {
        return state.reviewBuckets().stream().sorted(Comparator.comparingInt(AdminCatalogReviewBucket::count).reversed()).toList();
    }

    private List<AdminCatalogRuleImpact> sortedRuleImpacts(final AdminCatalogViewState state) {
        return state.ruleImpacts().stream().sorted(Comparator.comparingInt(AdminCatalogRuleImpact::lossCount).thenComparingInt(AdminCatalogRuleImpact::winCount).reversed()).toList();
    }

    private List<AdminCatalogReviewBucket> issueBuckets(final AdminCatalogViewState state) {
        final List<AdminCatalogReviewBucket> ordered = new ArrayList<>();
        this.addIssueBucket(state, ordered, "live-misc-items");
        this.addIssueBucket(state, ordered, "blocked-paths");
        this.addIssueBucket(state, ordered, "no-root-path");
        this.addIssueBucket(state, ordered, "sell-only-review");
        if (ordered.isEmpty()) {
            ordered.addAll(this.sortedBuckets(state).stream().limit(4).toList());
        }
        return ordered;
    }

    private void addIssueBucket(final AdminCatalogViewState state, final List<AdminCatalogReviewBucket> ordered, final String bucketId) {
        final AdminCatalogReviewBucket bucket = state.findReviewBucket(bucketId);
        if (bucket != null) {
            ordered.add(bucket);
        }
    }

    private ItemStack actionButton(final Material material, final String name) { return this.item(material, name, List.of()); }
    private ItemStack confirmApplyButton() { return this.item(Material.REDSTONE_BLOCK, "Confirm Apply", List.of("Publishes the current generated catalog", "and reloads the plugin.", "Use only after review.")); }
    private ItemStack cancelApplyButton() { return this.item(Material.BARRIER, "Cancel Apply", List.of("Returns to a normal preview state", "without publishing changes.")); }
    private ItemStack button(final Material material, final String name) { return this.item(material, name, List.of()); }

    private ItemStack item(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private boolean isApplyConfirmState(final AdminCatalogViewState state) { return this.isApplyConfirmAction(state.lastAction()); }
    private boolean isApplyConfirmAction(final String action) { return APPLY_CONFIRM_ACTION.equalsIgnoreCase(action); }
    private String displayAction(final String action) { return this.isApplyConfirmAction(action) ? "apply confirm" : action.replace('-', ' '); }
    private String pretty(final String raw) { return raw == null ? "" : raw.replace('-', ' '); }
}

