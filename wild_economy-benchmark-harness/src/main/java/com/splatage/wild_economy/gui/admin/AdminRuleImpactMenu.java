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
    private static final int ITEMS_PER_PAGE = 36;
    private static final int SLOT_PREV = 46;
    private static final int SLOT_NEXT = 47;
    private static final int SLOT_SORT = 48;

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void openList(final Player player, final AdminCatalogViewState state, final int pageIndex, final String sortMode) {
        final List<AdminCatalogRuleImpact> ruleImpacts = this.sortedRuleImpacts(state, sortMode);
        final int pageCount = Math.max(1, (int) Math.ceil((double) ruleImpacts.size() / ITEMS_PER_PAGE));
        final int safePage = Math.max(0, Math.min(pageIndex, pageCount - 1));
        final AdminMenuHolder holder = AdminMenuHolder.ruleImpactList(state, safePage, sortMode);
        final Inventory inventory = holder.createInventory(54, "Admin - Rule Impacts");

        int slot = 0;
        for (final AdminCatalogRuleImpact ruleImpact : this.pageSlice(ruleImpacts, safePage)) {
            inventory.setItem(slot++, this.ruleItem(ruleImpact));
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(SLOT_PREV, this.navButton(Material.SPECTRAL_ARROW, "Prev Page", safePage > 0));
        inventory.setItem(SLOT_NEXT, this.navButton(Material.ARROW, "Next Page", safePage + 1 < pageCount));
        inventory.setItem(SLOT_SORT, this.sortButton(sortMode));
        inventory.setItem(49, this.item(Material.BOOK, "Page", List.of("Page " + (safePage + 1) + " / " + pageCount, "Sort: " + this.prettySort(sortMode))));
        inventory.setItem(50, this.button(Material.COMPASS, "Root"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void openDetail(final Player player, final AdminCatalogViewState state, final String ruleId, final int pageIndex, final String sortMode) {
        final AdminCatalogRuleImpact ruleImpact = state.findRuleImpact(ruleId);
        if (ruleImpact == null) {
            player.sendMessage("Unknown rule impact: " + ruleId);
            this.openList(player, state, pageIndex, sortMode);
            return;
        }
        final AdminMenuHolder holder = AdminMenuHolder.ruleImpactDetail(state, ruleId, pageIndex, sortMode);
        final Inventory inventory = holder.createInventory(54, "Rule - " + ruleImpact.ruleId());
        inventory.setItem(4, this.ruleSummaryItem(ruleImpact));
        inventory.setItem(8, this.ruleContextItem(ruleImpact, sortMode));
        inventory.setItem(10, this.policyMapItem("Winning policies", ruleImpact.winningPolicies()));
        inventory.setItem(12, this.lossRuleItem(ruleImpact));
        inventory.setItem(14, this.policyMapItem("Lost to policies", ruleImpact.lostToPolicies()));
        inventory.setItem(16, this.topLossRuleButton(ruleImpact));
        inventory.setItem(20, this.sampleGroupButton(Material.BOOK, "Matched samples", ruleImpact.sampleMatchedItems(), SAMPLE_GROUP_MATCHED));
        inventory.setItem(22, this.sampleGroupButton(Material.LIME_DYE, "Winning samples", ruleImpact.sampleWinningItems(), SAMPLE_GROUP_WINNING));
        inventory.setItem(24, this.sampleGroupButton(Material.RED_DYE, "Lost samples", ruleImpact.sampleLostItems(), SAMPLE_GROUP_LOST));
        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.COMPARATOR, "Rule List"));
        inventory.setItem(50, this.button(Material.COMPASS, "Root"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void openSampleDetail(final Player player, final AdminCatalogViewState state, final String ruleId, final String sampleGroupId, final int pageIndex, final String sortMode) {
        final AdminCatalogRuleImpact ruleImpact = state.findRuleImpact(ruleId);
        if (ruleImpact == null) {
            player.sendMessage("Unknown rule impact: " + ruleId);
            this.openList(player, state, pageIndex, sortMode);
            return;
        }
        final List<String> items = this.sampleItems(ruleImpact, sampleGroupId);
        final AdminMenuHolder holder = AdminMenuHolder.ruleImpactSampleDetail(state, ruleId, sampleGroupId, pageIndex, sortMode);
        final Inventory inventory = holder.createInventory(54, "Rule Samples - " + this.prettySampleGroup(sampleGroupId));
        inventory.setItem(4, this.sampleGroupSummaryItem(ruleImpact, sampleGroupId, items));
        int slot = 18;
        for (final String itemKey : items) {
            if (slot >= 45) break;
            inventory.setItem(slot++, this.itemButton(itemKey, this.prettySampleGroup(sampleGroupId) + " sample"));
        }
        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.COMPARATOR, "Rule Detail"));
        inventory.setItem(50, this.button(Material.COMPASS, "Root"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        switch (holder.viewType()) {
            case RULE_IMPACT_LIST -> this.handleListClick(event, player, holder);
            case RULE_IMPACT_DETAIL -> this.handleDetailClick(event, player, holder);
            case RULE_IMPACT_SAMPLE_DETAIL -> this.handleSampleDetailClick(event, player, holder);
            default -> { }
        }
    }

    private void handleListClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final List<AdminCatalogRuleImpact> ruleImpacts = this.sortedRuleImpacts(holder.state(), holder.sortMode());
        final List<AdminCatalogRuleImpact> page = this.pageSlice(ruleImpacts, holder.pageIndex());
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            if (slot < page.size()) this.adminMenuRouter.openRuleImpactDetail(player, holder.state(), page.get(slot).ruleId(), holder.pageIndex(), holder.sortMode());
            return;
        }
        switch (slot) {
            case 45 -> this.adminMenuRouter.openRoot(player, holder.state());
            case SLOT_PREV -> { if (holder.pageIndex() > 0) this.adminMenuRouter.openRuleImpactList(player, holder.state(), holder.pageIndex() - 1, holder.sortMode()); }
            case SLOT_NEXT -> {
                final int pageCount = Math.max(1, (int) Math.ceil((double) ruleImpacts.size() / ITEMS_PER_PAGE));
                if (holder.pageIndex() + 1 < pageCount) this.adminMenuRouter.openRuleImpactList(player, holder.state(), holder.pageIndex() + 1, holder.sortMode());
            }
            case SLOT_SORT -> this.adminMenuRouter.openRuleImpactList(player, holder.state(), 0, this.toggleSort(holder.sortMode()));
            case 50 -> this.adminMenuRouter.openRoot(player, holder.state());
            case 53 -> player.closeInventory();
            default -> { }
        }
    }

    private void handleDetailClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final AdminCatalogRuleImpact ruleImpact = holder.state().findRuleImpact(holder.ruleId());
        if (ruleImpact == null) {
            this.adminMenuRouter.openRuleImpactList(player, holder.state(), holder.pageIndex(), holder.sortMode());
            return;
        }
        switch (slot) {
            case 16 -> {
                final String topLossRule = this.sortedRuleIds(ruleImpact.lostToRules()).stream().findFirst().orElse(null);
                if (topLossRule != null) this.adminMenuRouter.openRuleImpactDetail(player, holder.state(), topLossRule, holder.pageIndex(), holder.sortMode());
            }
            case 20 -> this.adminMenuRouter.openRuleImpactSampleDetail(player, holder.state(), ruleImpact.ruleId(), SAMPLE_GROUP_MATCHED, holder.pageIndex(), holder.sortMode());
            case 22 -> this.adminMenuRouter.openRuleImpactSampleDetail(player, holder.state(), ruleImpact.ruleId(), SAMPLE_GROUP_WINNING, holder.pageIndex(), holder.sortMode());
            case 24 -> this.adminMenuRouter.openRuleImpactSampleDetail(player, holder.state(), ruleImpact.ruleId(), SAMPLE_GROUP_LOST, holder.pageIndex(), holder.sortMode());
            case 45, 49 -> this.adminMenuRouter.openRuleImpactList(player, holder.state(), holder.pageIndex(), holder.sortMode());
            case 50 -> this.adminMenuRouter.openRoot(player, holder.state());
            case 53 -> player.closeInventory();
            default -> { }
        }
    }

    private void handleSampleDetailClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final AdminCatalogRuleImpact ruleImpact = holder.state().findRuleImpact(holder.ruleId());
        if (ruleImpact == null) {
            this.adminMenuRouter.openRuleImpactList(player, holder.state(), holder.pageIndex(), holder.sortMode());
            return;
        }
        final List<String> items = this.sampleItems(ruleImpact, holder.sampleGroupId());
        if (slot >= 18 && slot < 45) {
            final int index = slot - 18;
            if (index < items.size()) {
                this.adminMenuRouter.openItemInspector(player, holder.state(), items.get(index), null, holder.ruleId(), holder.pageIndex(), holder.sortMode());
            }
            return;
        }
        switch (slot) {
            case 45, 49 -> this.adminMenuRouter.openRuleImpactDetail(player, holder.state(), holder.ruleId(), holder.pageIndex(), holder.sortMode());
            case 50 -> this.adminMenuRouter.openRoot(player, holder.state());
            case 53 -> player.closeInventory();
            default -> { }
        }
    }

    private List<String> sortedRuleIds(final Map<String, Integer> counts) { return counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).map(Map.Entry::getKey).toList(); }
    private List<AdminCatalogRuleImpact> sortedRuleImpacts(final AdminCatalogViewState state, final String sortMode) {
        final List<AdminCatalogRuleImpact> impacts = new ArrayList<>(state.ruleImpacts());
        switch (sortMode == null ? "loss" : sortMode.toLowerCase(Locale.ROOT)) {
            case "win" -> impacts.sort(Comparator.comparingInt(AdminCatalogRuleImpact::winCount).reversed().thenComparing(AdminCatalogRuleImpact::ruleId));
            case "name" -> impacts.sort(Comparator.comparing(AdminCatalogRuleImpact::ruleId));
            default -> impacts.sort(Comparator.comparingInt(AdminCatalogRuleImpact::lossCount).thenComparingInt(AdminCatalogRuleImpact::winCount).reversed());
        }
        return impacts;
    }
    private List<AdminCatalogRuleImpact> pageSlice(final List<AdminCatalogRuleImpact> impacts, final int pageIndex) { final int from = Math.max(0, pageIndex) * ITEMS_PER_PAGE; if (from >= impacts.size()) return List.of(); final int to = Math.min(impacts.size(), from + ITEMS_PER_PAGE); return impacts.subList(from, to); }
    private String toggleSort(final String sortMode) { return switch (sortMode == null ? "loss" : sortMode.toLowerCase(Locale.ROOT)) { case "loss" -> "win"; case "win" -> "name"; default -> "loss"; }; }
    private String prettySort(final String sortMode) { return switch (sortMode == null ? "loss" : sortMode.toLowerCase(Locale.ROOT)) { case "win" -> "wins"; case "name" -> "name"; default -> "losses"; }; }
    private List<String> sampleItems(final AdminCatalogRuleImpact ruleImpact, final String sampleGroupId) { return switch (sampleGroupId) { case SAMPLE_GROUP_MATCHED -> ruleImpact.sampleMatchedItems(); case SAMPLE_GROUP_WINNING -> ruleImpact.sampleWinningItems(); case SAMPLE_GROUP_LOST -> ruleImpact.sampleLostItems(); default -> List.of(); }; }

    

    private ItemStack navButton(final Material material, final String name, final boolean enabled) {
        final List<String> lore = new ArrayList<>();
        lore.add(enabled ? "Click to navigate." : "No further pages.");
        return this.item(enabled ? material : Material.GRAY_DYE, name, lore);
    }

    private ItemStack sortButton(final String sortMode) {
        final String safeSort = sortMode == null ? "loss" : sortMode.toLowerCase(Locale.ROOT);
        final String nextSort = this.toggleSort(safeSort);
        return this.item(
            Material.HOPPER,
            "Sort: " + this.prettySort(safeSort),
            List.of("Click to switch sort.", "Next: " + this.prettySort(nextSort))
        );
    }

    private ItemStack ruleContextItem(final AdminCatalogRuleImpact ruleImpact, final String sortMode) {
        final List<String> lore = new ArrayList<>();
        lore.add("Current sort: " + this.prettySort(sortMode));
        lore.add("Fallback rule: " + ruleImpact.fallbackRule());
        if (ruleImpact.hasMatchCriteria()) {
            lore.add("This rule has explicit match criteria.");
        } else {
            lore.add("This rule acts as a fallback.");
        }
        if (!ruleImpact.lostToRules().isEmpty()) {
            final Map.Entry<String, Integer> topLoss = ruleImpact.lostToRules().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .findFirst()
                .orElse(null);
            if (topLoss != null) {
                lore.add("Top conflict: " + topLoss.getKey() + " (" + topLoss.getValue() + ")");
            }
        }
        return this.item(Material.BOOK, "Rule context", lore);
    }

    private ItemStack topLossRuleButton(final AdminCatalogRuleImpact ruleImpact) {
        if (ruleImpact.lostToRules().isEmpty()) {
            return this.item(Material.GRAY_DYE, "Top losing rule", List.of("No losing-rule breakdown recorded."));
        }
        final Map.Entry<String, Integer> topLoss = ruleImpact.lostToRules().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .findFirst()
            .orElse(null);
        if (topLoss == null) {
            return this.item(Material.GRAY_DYE, "Top losing rule", List.of("No losing-rule breakdown recorded."));
        }
        return this.item(
            Material.REDSTONE_TORCH,
            "Top losing rule",
            List.of(topLoss.getKey(), "Losses: " + topLoss.getValue(), "Click to open that rule.")
        );
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

