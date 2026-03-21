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

        int matchedSlot = 18;
        for (final String itemKey : ruleImpact.sampleMatchedItems()) {
            if (matchedSlot >= 27) {
                break;
            }
            inventory.setItem(matchedSlot, this.itemButton(itemKey, "Matched sample"));
            matchedSlot++;
        }

        int winSlot = 27;
        for (final String itemKey : ruleImpact.sampleWinningItems()) {
            if (winSlot >= 36) {
                break;
            }
            inventory.setItem(winSlot, this.itemButton(itemKey, "Winning sample"));
            winSlot++;
        }

        int lostSlot = 36;
        for (final String itemKey : ruleImpact.sampleLostItems()) {
            if (lostSlot >= 45) {
                break;
            }
            inventory.setItem(lostSlot, this.itemButton(itemKey, "Lost sample"));
            lostSlot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.COMPARATOR, "Rule List"));
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

        if (slot >= 18 && slot < 27) {
            final int index = slot - 18;
            if (index < ruleImpact.sampleMatchedItems().size()) {
                this.adminMenuRouter.openItemInspector(player, holder.state(), ruleImpact.sampleMatchedItems().get(index), null, ruleImpact.ruleId());
            }
            return;
        }

        if (slot >= 27 && slot < 36) {
            final int index = slot - 27;
            if (index < ruleImpact.sampleWinningItems().size()) {
                this.adminMenuRouter.openItemInspector(player, holder.state(), ruleImpact.sampleWinningItems().get(index), null, ruleImpact.ruleId());
            }
            return;
        }

        if (slot >= 36 && slot < 45) {
            final int index = slot - 36;
            if (index < ruleImpact.sampleLostItems().size()) {
                this.adminMenuRouter.openItemInspector(player, holder.state(), ruleImpact.sampleLostItems().get(index), null, ruleImpact.ruleId());
            }
            return;
        }

        switch (slot) {
            case 45, 49 -> this.adminMenuRouter.openRuleImpactList(player, holder.state());
            case 53 -> player.closeInventory();
            default -> {
            }
        }
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
        lore.add("Matched / won / lost samples below");
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
}

