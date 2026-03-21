package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminRuleImpactMenu {

    private static final String SAMPLE_GROUP_MATCHED = "matched";
    private static final String SAMPLE_GROUP_WINNING = "winning";
    private static final String SAMPLE_GROUP_LOST = "lost";

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void openList(final Player player, final AdminCatalogViewState state) {
        final AdminMenuHolder holder = AdminMenuHolder.ruleImpactList(state);
        final Inventory inventory = holder.createInventory(54, "Admin - Rule Impacts");
        final List<AdminCatalogRuleImpact> ruleImpacts = this.sortedRuleImpacts(state);

        int slot = 0;
        for (final AdminCatalogRuleImpact ruleImpact : ruleImpacts) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.ruleItem(ruleImpact));
            slot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.button(Material.BOOK, "Refresh Root"));

        player.openInventory(inventory);
    }

    public void openDetail(final Player player, final AdminCatalogViewState state, final String ruleId) {
        final AdminCatalogRuleImpact ruleImpact = state.findRuleImpact(ruleId);
        if (ruleImpact == null) {
            player.sendMessage("Unknown rule impact: " + ruleId);
            this.openList(player, state);
            return;
        }

        final AdminMenuHolder holder = AdminMenuHolder.ruleImpactDetail(state, ruleId);
        final Inventory inventory = holder.createInventory(54, "Rule - " + ruleImpact.ruleId());

        inventory.setItem(4, this.ruleSummaryItem(ruleImpact));
        inventory.setItem(10, this.policyMapItem("Winning policies", ruleImpact.winningPolicies()));
        inventory.setItem(12, this.lossRuleItem(ruleImpact));
        inventory.setItem(14, this.policyMapItem("Lost to policies", ruleImpact.lostToPolicies()));

        inventory.setItem(20, this.sampleGroupButton(Material.BOOK, "Matched samples", ruleImpact.sampleMatchedItems(), SAMPLE_GROUP_MATCHED));
        inventory.setItem(22, this.sampleGroupButton(Material.LIME_DYE, "Winning samples", ruleImpact.sampleWinningItems(), SAMPLE_GROUP_WINNING));
        inventory.setItem(24, this.sampleGroupButton(Material.RED_DYE, "Lost samples", ruleImpact.sampleLostItems(), SAMPLE_GROUP_LOST));

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.COMPARATOR, "Rule List"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void openSampleDetail(
        final Player player,
        final AdminCatalogViewState state,
        final String ruleId,
        final String sampleGroupId
    ) {
        final AdminCatalogRuleImpact ruleImpact = state.findRuleImpact(ruleId);
        if (ruleImpact == null) {
            player.sendMessage("Unknown rule impact: " + ruleId);
            this.openList(player, state);
            return;
        }

        final List<String> items = this.sampleItems(ruleImpact, sampleGroupId);
        final AdminMenuHolder holder = AdminMenuHolder.ruleImpactSampleDetail(state, ruleId, sampleGroupId);
        final Inventory inventory = holder.createInventory(54, "Rule Samples - " + this.prettySampleGroup(sampleGroupId));

        inventory.setItem(4, this.sampleGroupSummaryItem(ruleImpact, sampleGroupId, items));

        int slot = 18;
        for (final String itemKey : items) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.itemButton(itemKey, this.prettySampleGroup(sampleGroupId) + " sample"));
            slot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.COMPARATOR, "Rule Detail"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (holder.viewType()) {
            case RULE_IMPACT_LIST -> this.handleListClick(event, player, holder.state());
            case RULE_IMPACT_DETAIL -> this.handleDetailClick(event, player, holder);
            case RULE_IMPACT_SAMPLE_DETAIL -> this.handleSampleDetailClick(event, player, holder);
            default -> {
            }
        }
    }

    private void handleListClick(final InventoryClickEvent event, final Player player, final AdminCatalogViewState state) {
        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final List<AdminCatalogRuleImpact> ruleImpacts = this.sortedRuleImpacts(state);
            if (slot < ruleImpacts.size()) {
                this.adminMenuRouter.openRuleImpactDetail(player, state, ruleImpacts.get(slot).ruleId());
            }
            return;
        }
        switch (slot) {
            case 45 -> this.adminMenuRouter.openRoot(player, state);
            case 49 -> player.closeInventory();
            case 53 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false);
            default -> {
            }
        }
    }

    private void handleDetailClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final AdminCatalogRuleImpact ruleImpact = holder.state().findRuleImpact(holder.ruleId());
        if (ruleImpact == null) {
            this.adminMenuRouter.openRuleImpactList(player, holder.state());
            return;
        }

        switch (slot) {
            case 20 -> this.adminMenuRouter.openRuleImpactSampleDetail(player, holder.state(), ruleImpact.ruleId(), SAMPLE_GROUP_MATCHED);
            case 22 -> this.adminMenuRouter.openRuleImpactSampleDetail(player, holder.state(), ruleImpact.ruleId(), SAMPLE_GROUP_WINNING);
            case 24 -> this.adminMenuRouter.openRuleImpactSampleDetail(player, holder.state(), ruleImpact.ruleId(), SAMPLE_GROUP_LOST);
            case 45, 49 -> this.adminMenuRouter.openRuleImpactList(player, holder.state());
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleSampleDetailClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final AdminCatalogRuleImpact ruleImpact = holder.state().findRuleImpact(holder.ruleId());
        if (ruleImpact == null) {
            this.adminMenuRouter.openRuleImpactList(player, holder.state());
            return;
        }

        final List<String> items = this.sampleItems(ruleImpact, holder.sampleGroupId());
        if (slot >= 18 && slot < 45) {
            final int index = slot - 18;
            if (index < items.size()) {
                this.adminMenuRouter.openItemInspector(player, holder.state(), items.get(index), null, holder.ruleId());
            }
            return;
        }

        switch (slot) {
            case 45, 49 -> this.adminMenuRouter.openRuleImpactDetail(player, holder.state(), holder.ruleId());
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private List<String> sortedRuleIds(final Map<String, Integer> counts) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .toList();
    }

    private List<AdminCatalogRuleImpact> sortedRuleImpacts(final AdminCatalogViewState state) {
        final List<AdminCatalogRuleImpact> ruleImpacts = new ArrayList<>(state.ruleImpacts());
        ruleImpacts.sort(
            Comparator.comparingInt(AdminCatalogRuleImpact::lossCount)
                .thenComparingInt(AdminCatalogRuleImpact::winCount)
                .reversed()
        );
        return ruleImpacts;
    }

    private List<String> sampleItems(final AdminCatalogRuleImpact ruleImpact, final String sampleGroupId) {
        return switch (sampleGroupId) {
            case SAMPLE_GROUP_MATCHED -> ruleImpact.sampleMatchedItems();
            case SAMPLE_GROUP_WINNING -> ruleImpact.sampleWinningItems();
            case SAMPLE_GROUP_LOST -> ruleImpact.sampleLostItems();
            default -> List.of();
        };
    }

    private ItemStack ruleItem(final AdminCatalogRuleImpact ruleImpact) {
        final List<String> lore = new ArrayList<>();
        lore.add("Fallback: " + ruleImpact.fallbackRule());
        lore.add("Matches: " + ruleImpact.matchCount());
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
        lore.add("Click to open rule detail.");
        return this.item(Material.COMPARATOR, ruleImpact.ruleId(), lore);
    }

    private ItemStack ruleSummaryItem(final AdminCatalogRuleImpact ruleImpact) {
        final List<String> lore = new ArrayList<>();
        lore.add("Fallback rule: " + ruleImpact.fallbackRule());
        lore.add("Has match criteria: " + ruleImpact.hasMatchCriteria());
        lore.add("Match count: " + ruleImpact.matchCount());
        lore.add("Win count: " + ruleImpact.winCount());
        lore.add("Loss count: " + ruleImpact.lossCount());
        lore.add("Open matched / won / lost sample lists below.");
        return this.item(Material.REPEATER, ruleImpact.ruleId(), lore);
    }

    private ItemStack lossRuleItem(final AdminCatalogRuleImpact ruleImpact) {
        final List<String> lore = new ArrayList<>();
        if (ruleImpact.lostToRules().isEmpty()) {
            lore.add("No losing-rule breakdown recorded.");
        } else {
            for (final Map.Entry<String, Integer> entry : ruleImpact.lostToRules().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .toList()) {
                lore.add(entry.getKey() + ": " + entry.getValue());
            }
        }
        return this.item(Material.REDSTONE_TORCH, "Lost to rules", lore);
    }

    private ItemStack policyMapItem(final String title, final Map<CatalogPolicy, Integer> counts) {
        final List<String> lore = new ArrayList<>();
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            lore.add(policy.name() + ": " + counts.getOrDefault(policy, Integer.valueOf(0)));
        }
        return this.item(Material.PAPER, title, lore);
    }

    private ItemStack sampleGroupButton(
        final Material material,
        final String title,
        final List<String> items,
        final String sampleGroupId
    ) {
        final List<String> lore = new ArrayList<>();
        lore.add("Count: " + items.size());
        if (items.isEmpty()) {
            lore.add("No samples recorded.");
        } else {
            for (final String itemKey : items.stream().limit(4).toList()) {
                lore.add(this.displayItemKey(itemKey));
            }
            lore.add("Click to open sample detail.");
        }
        return this.item(material, title, lore);
    }

    private ItemStack sampleGroupSummaryItem(
        final AdminCatalogRuleImpact ruleImpact,
        final String sampleGroupId,
        final List<String> items
    ) {
        final List<String> lore = new ArrayList<>();
        lore.add("Rule: " + ruleImpact.ruleId());
        lore.add("Group: " + this.prettySampleGroup(sampleGroupId));
        lore.add("Count: " + items.size());
        if (SAMPLE_GROUP_LOST.equals(sampleGroupId) && !ruleImpact.lostToRules().isEmpty()) {
            final Map.Entry<String, Integer> topLoss = ruleImpact.lostToRules().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .findFirst()
                .orElse(null);
            if (topLoss != null) {
                lore.add("Top losing rule: " + topLoss.getKey() + " (" + topLoss.getValue() + ")");
            }
        }
        lore.add("Click a sample item below to inspect it.");
        return this.item(Material.BOOK, this.prettySampleGroup(sampleGroupId) + " samples", lore);
    }

    private ItemStack itemButton(final String itemKey, final String footer) {
        final Material material = this.resolveMaterial(itemKey);
        return this.item(
            material == null ? Material.BARRIER : material,
            this.displayItemKey(itemKey),
            List.of(itemKey, footer)
        );
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

    private String displayItemKey(final String itemKey) {
        return itemKey.replace("minecraft:", "").replace('_', ' ');
    }

    private String prettySampleGroup(final String sampleGroupId) {
        return switch (sampleGroupId) {
            case SAMPLE_GROUP_MATCHED -> "Matched";
            case SAMPLE_GROUP_WINNING -> "Winning";
            case SAMPLE_GROUP_LOST -> "Lost";
            default -> "Unknown";
        };
    }
}

