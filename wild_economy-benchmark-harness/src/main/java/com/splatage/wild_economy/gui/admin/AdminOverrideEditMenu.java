package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogDecisionTrace;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogItemKeys;
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

public final class AdminOverrideEditMenu {

    private static final int SLOT_CONTEXT = 13;
    private static final int SLOT_POLICY = 20;
    private static final int SLOT_STOCK = 22;
    private static final int SLOT_ENVELOPE = 24;
    private static final int SLOT_NOTE = 31;
    private static final int SLOT_DELETE = 38;
    private static final int SLOT_SAVE = 42;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_INSPECT = 49;
    private static final int SLOT_CLOSE = 53;

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void open(
        final Player player,
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId,
        final int pageIndex,
        final String sortMode,
        final String overridePolicy,
        final String overrideStockProfile,
        final String overrideEcoEnvelope,
        final String overrideNote,
        final String actionId
    ) {
        final AdminCatalogDecisionTrace trace = state.findTrace(itemKey);
        if (trace == null) {
            player.sendMessage("No generated catalog decision found for '" + itemKey + "'.");
            this.adminMenuRouter.openRoot(player);
            return;
        }
        final AdminCatalogPlanEntry planEntry = state.findPlanEntry(itemKey);
        final AdminMenuHolder holder = AdminMenuHolder.overrideEditor(
            state,
            trace.itemKey(),
            returnBucketId,
            returnRuleId,
            pageIndex,
            sortMode,
            overridePolicy,
            overrideStockProfile,
            overrideEcoEnvelope,
            overrideNote,
            actionId
        );
        final Inventory inventory = holder.createInventory(54, "Override - " + trace.displayName());

        inventory.setItem(SLOT_CONTEXT, this.contextItem(trace, planEntry));
        inventory.setItem(SLOT_POLICY, this.policyButton(holder.overridePolicy()));
        inventory.setItem(SLOT_STOCK, this.namedValueButton(Material.CHEST, "Stock profile", holder.overrideStockProfile(), this.adminMenuRouter.availableStockProfiles()));
        inventory.setItem(SLOT_ENVELOPE, this.namedValueButton(Material.HONEYCOMB, "Eco envelope", holder.overrideEcoEnvelope(), this.adminMenuRouter.availableEcoEnvelopes()));
        inventory.setItem(SLOT_NOTE, this.noteButton(holder.overrideNote()));
        inventory.setItem(SLOT_DELETE, this.deleteButton(trace.manualOverrideApplied(), "delete-confirm".equals(holder.actionId())));
        inventory.setItem(SLOT_SAVE, this.saveButton(trace.manualOverrideApplied()));
        inventory.setItem(SLOT_BACK, this.button(Material.ARROW, "Back"));
        inventory.setItem(SLOT_INSPECT, this.button(Material.COMPASS, "Inspector"));
        inventory.setItem(SLOT_CLOSE, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (event.getRawSlot()) {
            case SLOT_POLICY -> this.adminMenuRouter.openOverrideEditor(
                player,
                holder.state(),
                holder.itemKey(),
                holder.returnBucketId(),
                holder.returnRuleId(),
                holder.pageIndex(),
                holder.sortMode(),
                this.adminMenuRouter.nextPolicy(holder.overridePolicy()),
                holder.overrideStockProfile(),
                holder.overrideEcoEnvelope(),
                holder.overrideNote(),
                null
            );
            case SLOT_STOCK -> this.adminMenuRouter.openOverrideEditor(
                player,
                holder.state(),
                holder.itemKey(),
                holder.returnBucketId(),
                holder.returnRuleId(),
                holder.pageIndex(),
                holder.sortMode(),
                holder.overridePolicy(),
                this.adminMenuRouter.nextStockProfile(holder.overrideStockProfile()),
                holder.overrideEcoEnvelope(),
                holder.overrideNote(),
                null
            );
            case SLOT_ENVELOPE -> this.adminMenuRouter.openOverrideEditor(
                player,
                holder.state(),
                holder.itemKey(),
                holder.returnBucketId(),
                holder.returnRuleId(),
                holder.pageIndex(),
                holder.sortMode(),
                holder.overridePolicy(),
                holder.overrideStockProfile(),
                this.adminMenuRouter.nextEcoEnvelope(holder.overrideEcoEnvelope()),
                holder.overrideNote(),
                null
            );
            case SLOT_NOTE -> this.adminMenuRouter.openOverrideEditor(
                player,
                holder.state(),
                holder.itemKey(),
                holder.returnBucketId(),
                holder.returnRuleId(),
                holder.pageIndex(),
                holder.sortMode(),
                holder.overridePolicy(),
                holder.overrideStockProfile(),
                holder.overrideEcoEnvelope(),
                this.adminMenuRouter.nextOverrideNote(holder.overrideNote()),
                null
            );
            case SLOT_DELETE -> {
                final AdminCatalogDecisionTrace trace = holder.state().findTrace(holder.itemKey());
                if (trace == null || !trace.manualOverrideApplied()) {
                    return;
                }
                if ("delete-confirm".equals(holder.actionId())) {
                    this.adminMenuRouter.removeManualOverrideAndInspect(
                        player,
                        holder.state(),
                        holder.itemKey(),
                        holder.returnBucketId(),
                        holder.returnRuleId(),
                        holder.pageIndex(),
                        holder.sortMode()
                    );
                } else {
                    this.adminMenuRouter.openOverrideEditor(
                        player,
                        holder.state(),
                        holder.itemKey(),
                        holder.returnBucketId(),
                        holder.returnRuleId(),
                        holder.pageIndex(),
                        holder.sortMode(),
                        holder.overridePolicy(),
                        holder.overrideStockProfile(),
                        holder.overrideEcoEnvelope(),
                        holder.overrideNote(),
                        "delete-confirm"
                    );
                }
            }
            case SLOT_SAVE -> this.adminMenuRouter.saveManualOverrideAndInspect(
                player,
                holder.state(),
                holder.itemKey(),
                holder.returnBucketId(),
                holder.returnRuleId(),
                holder.pageIndex(),
                holder.sortMode(),
                holder.overridePolicy(),
                holder.overrideStockProfile(),
                holder.overrideEcoEnvelope(),
                holder.overrideNote()
            );
            case SLOT_BACK -> this.adminMenuRouter.goBack(player);
            case SLOT_INSPECT -> this.adminMenuRouter.openItemInspector(
                player,
                holder.state(),
                holder.itemKey(),
                holder.returnBucketId(),
                holder.returnRuleId(),
                holder.pageIndex(),
                holder.sortMode()
            );
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private ItemStack contextItem(final AdminCatalogDecisionTrace trace, final AdminCatalogPlanEntry planEntry) {
        final List<String> lore = new ArrayList<>();
        lore.add(trace.itemKey());
        lore.add("Final policy: " + trace.finalPolicy().name());
        lore.add("Current stock profile: " + trace.stockProfile());
        lore.add("Current eco envelope: " + trace.ecoEnvelope());
        lore.add("Override exists: " + (trace.manualOverrideApplied() ? "yes" : "no"));
        if (planEntry != null) {
            lore.add("Policy profile: " + planEntry.policyProfileId());
            lore.add("Runtime: " + planEntry.runtimePolicy());
            lore.add("Players can buy: " + planEntry.buyEnabled());
            lore.add("Players can sell: " + planEntry.sellEnabled());
            lore.add("Stock-backed: " + planEntry.stockBacked());
            lore.add("Unlimited buy: " + planEntry.unlimitedBuy());
        }
        return this.item(this.resolveMaterial(trace.itemKey()), trace.displayName(), lore);
    }

    private ItemStack policyButton(final String overridePolicy) {
        final List<String> lore = new ArrayList<>();
        lore.add("Current: " + safeDisplay(overridePolicy));
        lore.add(this.adminMenuRouter.policyBehaviorSummary(overridePolicy));
        lore.add("Available: " + this.adminMenuRouter.availablePolicyIds().size());
        lore.add("Click to cycle.");
        return this.item(Material.COMPARATOR, "Policy", lore);
    }

    private ItemStack namedValueButton(final Material material, final String label, final String current, final List<String> values) {
        final List<String> lore = new ArrayList<>();
        lore.add("Current: " + safeDisplay(current));
        lore.add("Available: " + values.size());
        lore.add("Click to cycle.");
        return this.item(material, label, lore);
    }

    private ItemStack noteButton(final String note) {
        final List<String> lore = new ArrayList<>();
        final String safe = note == null || note.isBlank() ? "<empty>" : note;
        lore.add(safe);
        lore.add("Click to cycle note mode.");
        lore.add("Free-form note editing is deferred.");
        return this.item(Material.NAME_TAG, "Note", lore);
    }

    private ItemStack deleteButton(final boolean overrideExists, final boolean deleteConfirmArmed) {
        if (!overrideExists) {
            return this.item(Material.GRAY_DYE, "Remove override", List.of("No current override exists."));
        }
        if (deleteConfirmArmed) {
            return this.item(Material.REDSTONE_BLOCK, "Confirm remove override", List.of("Click to delete the current override."));
        }
        return this.item(Material.BARRIER, "Remove override", List.of("Click once to arm delete."));
    }

    private ItemStack saveButton(final boolean overrideExists) {
        return this.item(Material.LIME_DYE, overrideExists ? "Update override" : "Create override", List.of("Write manual-overrides.yml and refresh preview."));
    }

    private ItemStack button(final Material material, final String name) {
        return this.item(material, name, List.of());
    }

    private ItemStack item(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
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

    private static String safeDisplay(final String value) {
        return value == null || value.isBlank() ? "<empty>" : value;
    }

    private Material resolveMaterial(final String itemKey) {
        final String enumName = AdminCatalogItemKeys.canonicalize(itemKey).replace("minecraft:", "").toUpperCase(Locale.ROOT);
        return Material.matchMaterial(enumName);
    }
}

