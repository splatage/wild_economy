package com.splatage.wild_economy.gui.admin;

import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class AdminMenuListener implements Listener {

    private final AdminRootMenu adminRootMenu;
    private final AdminReviewBucketMenu adminReviewBucketMenu;
    private final AdminRuleImpactMenu adminRuleImpactMenu;
    private final AdminItemInspectorMenu adminItemInspectorMenu;
    private final AdminOverrideEditMenu adminOverrideEditMenu;

    public AdminMenuListener(
        final AdminRootMenu adminRootMenu,
        final AdminReviewBucketMenu adminReviewBucketMenu,
        final AdminRuleImpactMenu adminRuleImpactMenu,
        final AdminItemInspectorMenu adminItemInspectorMenu,
        final AdminOverrideEditMenu adminOverrideEditMenu
    ) {
        this.adminRootMenu = Objects.requireNonNull(adminRootMenu, "adminRootMenu");
        this.adminReviewBucketMenu = Objects.requireNonNull(adminReviewBucketMenu, "adminReviewBucketMenu");
        this.adminRuleImpactMenu = Objects.requireNonNull(adminRuleImpactMenu, "adminRuleImpactMenu");
        this.adminItemInspectorMenu = Objects.requireNonNull(adminItemInspectorMenu, "adminItemInspectorMenu");
        this.adminOverrideEditMenu = Objects.requireNonNull(adminOverrideEditMenu, "adminOverrideEditMenu");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final AdminMenuHolder holder = AdminMenuRouter.getAdminMenuHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        switch (holder.viewType()) {
            case ROOT -> this.adminRootMenu.handleClick(event, holder);
            case REVIEW_BUCKET_LIST, REVIEW_BUCKET_DETAIL, REVIEW_BUCKET_SUBGROUP_DETAIL -> this.adminReviewBucketMenu.handleClick(event, holder);
            case RULE_IMPACT_LIST, RULE_IMPACT_DETAIL, RULE_IMPACT_SAMPLE_DETAIL -> this.adminRuleImpactMenu.handleClick(event, holder);
            case ITEM_INSPECTOR -> this.adminItemInspectorMenu.handleClick(event, holder);
            case OVERRIDE_EDITOR -> this.adminOverrideEditMenu.handleClick(event, holder);
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        final AdminMenuHolder holder = AdminMenuRouter.getAdminMenuHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        final int topSize = event.getView().getTopInventory().getSize();
        for (final int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
