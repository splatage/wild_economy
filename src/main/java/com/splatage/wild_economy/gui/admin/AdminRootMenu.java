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

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void open(final Player player, final AdminCatalogViewState state) {
        final AdminMenuHolder holder = AdminMenuHolder.root(state);
        final Inventory inventory = holder.createInventory(54, "Shop Admin");

        inventory.setItem(10, this.summaryItem(state.buildResult(), state.lastAction(), state));
        inventory.setItem(12, this.policyItem(state.buildResult()));
        inventory.setItem(14, this.reviewItem(state));

        inventory.setItem(28, this.actionButton(Material.LIME_STAINED_GLASS_PANE, "Preview"));
        inventory.setItem(29, this.actionButton(Material.YELLOW_STAINED_GLASS_PANE, "Validate"));
        inventory.setItem(30, this.actionButton(Material.PAPER, "Diff"));
        inventory.setItem(32, this.actionButton(Material.EMERALD_BLOCK, "Apply"));
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

        inventory.setItem(45, this.button(Material.ARROW, "Refresh"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        switch (slot) {
            case 28 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false);
            case 29 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "validate", false);
            case 30 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "diff", false);
            case 32 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "apply", true);
            case 34 -> this.adminMenuRouter.openReviewBucketList(player, holder.state());
            case 35 -> this.adminMenuRouter.openRuleImpactList(player, holder.state());
            case 45 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false);
            case 49 -> player.closeInventory();
            default -> {
                for (int i = 0; i < TOP_BUCKET_SLOTS.length; i++) {
                    if (slot == TOP_BUCKET_SLOTS[i]) {
                        final List<AdminCatalogReviewBucket> topBuckets = this.sortedBuckets(holder.state());
                        if (i < topBuckets.size()) {
                            this.adminMenuRouter.openReviewBucketDetail(player, holder.state(), topBuckets.get(i).bucketId());
                        }
                        return;
                    }
                }
                for (int i = 0; i < TOP_RULE_SLOTS.length; i++) {
                    if (slot == TOP_RULE_SLOTS[i]) {
                        final List<AdminCatalogRuleImpact> topRules = this.sortedRuleImpacts(holder.state());
                        if (i < topRules.size()) {
                            this.adminMenuRouter.openRuleImpactDetail(player, holder.state(), topRules.get(i).ruleId());
                        }
                        return;
                    }
                }
            }
        }
    }

    private ItemStack summaryItem(
        final AdminCatalogBuildResult result,
        final String lastAction,
        final AdminCatalogViewState state
    ) {
        final List<String> lore = new ArrayList<>();
        lore.add("Last action: " + lastAction);
        lore.add("Scanned: " + result.totalScanned());
        lore.add("Proposed: " + result.proposedEntries().size());
        lore.add("Live-enabled: " + result.liveEntries().size());
        lore.add("Disabled: " + result.disabledCount());
        lore.add("Unresolved: " + result.unresolvedCount());
        lore.add("Warnings: " + result.warningCount());
        lore.add("Errors: " + result.errorCount());
        final AdminCatalogReviewBucket topBucket = this.sortedBuckets(state).stream().findFirst().orElse(null);
        if (topBucket != null) {
            lore.add("Top review bucket: " + topBucket.bucketId() + " (" + topBucket.count() + ")");
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
        return this.item(
            Material.WRITABLE_BOOK,
            "Policy Split",
            List.of(
                "ALWAYS_AVAILABLE: " + counts.get(CatalogPolicy.ALWAYS_AVAILABLE),
                "EXCHANGE: " + counts.get(CatalogPolicy.EXCHANGE),
                "SELL_ONLY: " + counts.get(CatalogPolicy.SELL_ONLY),
                "DISABLED: " + counts.get(CatalogPolicy.DISABLED)
            )
        );
    }

    private ItemStack reviewItem(final AdminCatalogViewState state) {
        final List<String> lore = new ArrayList<>();
        lore.add("Review buckets: " + state.reviewBuckets().size());
        lore.add("Rule impacts: " + state.ruleImpacts().size());
        for (final AdminCatalogReviewBucket bucket : this.sortedBuckets(state).stream().limit(3).toList()) {
            lore.add(bucket.bucketId() + ": " + bucket.count());
        }
        lore.add("Use shortcuts below to drill in.");
        return this.item(Material.ENDER_CHEST, "Review Data", lore);
    }

    private ItemStack topBucketItem(final AdminCatalogReviewBucket bucket) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Count: " + bucket.count());
        for (final Map.Entry<String, Integer> entry : bucket.subgroupCounts().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .toList()) {
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
            final Map.Entry<String, Integer> topLoss = ruleImpact.lostToRules().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .findFirst()
                .orElse(null);
            if (topLoss != null) {
                lore.add("Top loss: " + topLoss.getKey() + " (" + topLoss.getValue() + ")");
            }
        }
        lore.add(ruleImpact.fallbackRule() ? "Fallback rule" : "Specific rule");
        lore.add("Click to open rule detail.");
        return this.item(Material.COMPARATOR, ruleImpact.ruleId(), lore);
    }

    private List<AdminCatalogReviewBucket> sortedBuckets(final AdminCatalogViewState state) {
        return state.reviewBuckets().stream()
            .sorted(Comparator.comparingInt(AdminCatalogReviewBucket::count).reversed())
            .toList();
    }

    private List<AdminCatalogRuleImpact> sortedRuleImpacts(final AdminCatalogViewState state) {
        return state.ruleImpacts().stream()
            .sorted(Comparator.comparingInt(AdminCatalogRuleImpact::lossCount)
                .thenComparingInt(AdminCatalogRuleImpact::winCount)
                .reversed())
            .toList();
    }

    private ItemStack actionButton(final Material material, final String name) {
        return this.item(material, name, List.of());
    }

    private ItemStack button(final Material material, final String name) {
        return this.item(material, name, List.of());
    }

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

    private String pretty(final String raw) {
        return raw.replace('-', ' ');
    }
}

