package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
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

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void open(final Player player, final AdminCatalogViewState state) {
        final AdminMenuHolder holder = AdminMenuHolder.root(state);
        final Inventory inventory = holder.createInventory(45, "Shop Admin");

        inventory.setItem(10, this.summaryItem(state.buildResult(), state.lastAction()));
        inventory.setItem(12, this.policyItem(state.buildResult()));
        inventory.setItem(14, this.reviewItem(state));

        inventory.setItem(28, this.actionButton(Material.LIME_STAINED_GLASS_PANE, "Preview"));
        inventory.setItem(29, this.actionButton(Material.YELLOW_STAINED_GLASS_PANE, "Validate"));
        inventory.setItem(30, this.actionButton(Material.PAPER, "Diff"));
        inventory.setItem(32, this.actionButton(Material.EMERALD_BLOCK, "Apply"));

        inventory.setItem(34, this.actionButton(Material.CHEST, "Review Buckets"));
        inventory.setItem(35, this.actionButton(Material.COMPARATOR, "Rule Impacts"));
        inventory.setItem(40, this.actionButton(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 28 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false);
            case 29 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "validate", false);
            case 30 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "diff", false);
            case 32 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "apply", true);
            case 34 -> this.adminMenuRouter.openReviewBucketList(player, holder.state());
            case 35 -> this.adminMenuRouter.openRuleImpactList(player, holder.state());
            case 40 -> player.closeInventory();
            default -> {
            }
        }
    }

    private ItemStack summaryItem(final AdminCatalogBuildResult result, final String lastAction) {
        return this.item(
            Material.BOOK,
            "Catalog Summary",
            List.of(
                "Last action: " + lastAction,
                "Scanned: " + result.totalScanned(),
                "Proposed: " + result.proposedEntries().size(),
                "Live-enabled: " + result.liveEntries().size(),
                "Disabled: " + result.disabledCount(),
                "Unresolved: " + result.unresolvedCount(),
                "Warnings: " + result.warningCount(),
                "Errors: " + result.errorCount()
            )
        );
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
        return this.item(
            Material.ENDER_CHEST,
            "Review Data",
            List.of(
                "Review buckets: " + state.reviewBuckets().size(),
                "Rule impacts: " + state.ruleImpacts().size(),
                "Use the buttons below to browse",
                "generated/generated-review-buckets.yml",
                "generated/generated-rule-impacts.yml"
            )
        );
    }

    private ItemStack actionButton(final Material material, final String name) {
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
}

