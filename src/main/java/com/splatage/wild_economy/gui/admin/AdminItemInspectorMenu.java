package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogDecisionTrace;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminItemInspectorMenu {

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void open(
        final Player player,
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId
    ) {
        final AdminCatalogDecisionTrace trace = state.findTrace(itemKey);
        if (trace == null) {
            player.sendMessage("No generated catalog decision found for '" + itemKey + "'.");
            this.adminMenuRouter.openRoot(player);
            return;
        }
        final AdminCatalogPlanEntry planEntry = state.findPlanEntry(itemKey);
        final AdminMenuHolder holder = AdminMenuHolder.itemInspector(state, trace.itemKey(), returnBucketId, returnRuleId);
        final Inventory inventory = holder.createInventory(54, "Inspect - " + trace.displayName());

        inventory.setItem(13, this.itemIcon(trace, planEntry));
        inventory.setItem(20, this.decisionItem(trace));
        inventory.setItem(22, this.runtimeItem(planEntry));
        inventory.setItem(24, this.ruleItem(trace, state));
        inventory.setItem(31, this.reviewBucketItem(trace, planEntry));
        inventory.setItem(33, this.actionHintItem(trace, state));

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.COMPASS, "Root"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 45 -> this.adminMenuRouter.goBack(player);
            case 49 -> this.adminMenuRouter.openRoot(player, holder.state());
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private ItemStack itemIcon(final AdminCatalogDecisionTrace trace, final AdminCatalogPlanEntry planEntry) {
        final Material material = this.resolveMaterial(trace.itemKey());
        final List<String> lore = new ArrayList<>();
        lore.add(trace.itemKey());
        lore.add("Category: " + trace.finalCategory().name());
        lore.add("Final policy: " + trace.finalPolicy().name());
        if (planEntry != null) {
            lore.add("Buy: " + planEntry.buyEnabled() + " @ " + String.valueOf(planEntry.buyPrice()));
            lore.add("Sell: " + planEntry.sellEnabled() + " @ " + String.valueOf(planEntry.sellPrice()));
        }
        return this.item(material == null ? Material.BARRIER : material, trace.displayName(), lore);
    }

    private ItemStack decisionItem(final AdminCatalogDecisionTrace trace) {
        final List<String> lore = new ArrayList<>();
        if (trace.classifiedCategory() == trace.finalCategory()) {
            lore.add("Category: " + trace.finalCategory().name());
        } else {
            lore.add("Category: " + trace.classifiedCategory().name() + " -> " + trace.finalCategory().name());
        }
        lore.add("Derivation: " + trace.derivationReason().name());
        lore.add("Depth: " + trace.derivationDepth());
        lore.add("Base suggested: " + trace.baseSuggestedPolicy().name());
        lore.add("Final policy: " + trace.finalPolicy().name());
        if (trace.postRuleAdjustment() != null && !trace.postRuleAdjustment().isBlank()) {
            lore.add("Adjustment: " + trace.postRuleAdjustment());
        }
        return this.item(Material.BOOK, "Decision", lore);
    }

    private ItemStack runtimeItem(final AdminCatalogPlanEntry planEntry) {
        final List<String> lore = new ArrayList<>();
        if (planEntry == null) {
            lore.add("No runtime plan entry recorded.");
        } else {
            lore.add("Runtime policy: " + planEntry.runtimePolicy());
            lore.add("Buy enabled: " + planEntry.buyEnabled());
            lore.add("Sell enabled: " + planEntry.sellEnabled());
            lore.add("Stock profile: " + planEntry.stockProfile());
            lore.add("Eco envelope: " + planEntry.ecoEnvelope());
            lore.add("Anchor: " + String.valueOf(planEntry.anchorValue()));
            lore.add("Buy price: " + String.valueOf(planEntry.buyPrice()));
            lore.add("Sell price: " + String.valueOf(planEntry.sellPrice()));
        }
        return this.item(Material.WRITABLE_BOOK, "Runtime", lore);
    }

    private ItemStack ruleItem(final AdminCatalogDecisionTrace trace, final AdminCatalogViewState state) {
        final List<String> lore = new ArrayList<>();
        if (trace.winningRuleId() == null || trace.winningRuleId().isBlank()) {
            lore.add("Decision source: none recorded.");
        } else if (trace.matchedRuleIds().contains(trace.winningRuleId())) {
            lore.add("Decision source: matched rule " + trace.winningRuleId());
        } else {
            lore.add("Decision source: fallback rule " + trace.winningRuleId());
        }
        lore.add("Manual override: " + trace.manualOverrideApplied());
        if (trace.matchedRuleIds().isEmpty()) {
            lore.add("Matched specific rules: none");
        } else {
            lore.add("Matched specific rules: " + trace.matchedRuleIds());
        }
        final List<String> matchedButLost = new ArrayList<>();
        for (final String matchedRuleId : trace.matchedRuleIds()) {
            if (!matchedRuleId.equals(trace.winningRuleId())) {
                matchedButLost.add(matchedRuleId);
            }
        }
        if (!matchedButLost.isEmpty()) {
            lore.add("Matched but lost: " + matchedButLost);
        }
        final AdminCatalogRuleImpact winningImpact = state.findRuleImpact(trace.winningRuleId());
        if (winningImpact != null) {
            lore.add("Rule wins: " + winningImpact.winCount());
            lore.add("Rule losses: " + winningImpact.lossCount());
        }
        return this.item(Material.COMPARATOR, "Rules", lore);
    }

    private ItemStack reviewBucketItem(final AdminCatalogDecisionTrace trace, final AdminCatalogPlanEntry planEntry) {
        final List<String> lore = new ArrayList<>();
        final List<String> bucketIds = this.findReviewBuckets(trace, planEntry);
        if (bucketIds.isEmpty()) {
            lore.add("No current review bucket membership.");
        } else {
            for (final String bucketId : bucketIds) {
                lore.add(bucketId);
            }
        }
        return this.item(Material.CHEST, "Review buckets", lore);
    }

    private ItemStack actionHintItem(final AdminCatalogDecisionTrace trace, final AdminCatalogViewState state) {
        final List<String> lore = new ArrayList<>();
        final List<String> reviewBuckets = this.findReviewBuckets(trace, state.findPlanEntry(trace.itemKey()));
        if (!reviewBuckets.isEmpty()) {
            lore.add("Bucket focus: " + reviewBuckets.get(0));
        }
        if (trace.finalCategory() == CatalogCategory.MISC && trace.finalPolicy() != CatalogPolicy.DISABLED) {
            lore.add("Likely next action: category review.");
        } else if (trace.derivationReason() == DerivationReason.NO_RECIPE_AND_NO_ROOT) {
            lore.add("Likely next action: root anchor or leave disabled.");
        } else if (trace.derivationReason() == DerivationReason.ALL_PATHS_BLOCKED) {
            lore.add("Likely next action: recipe-path review.");
        } else if (trace.finalPolicy() == CatalogPolicy.SELL_ONLY) {
            lore.add("Likely next action: review long-term SELL_ONLY fit.");
        } else {
            lore.add("Likely next action: none.");
        }
        return this.item(Material.LANTERN, "Admin hints", lore);
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

    private Material resolveMaterial(final String itemKey) {
        return Material.matchMaterial(itemKey.replace("minecraft:", "").toUpperCase(Locale.ROOT));
    }
}

