package com.splatage.wild_economy.gui.admin;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AdminMenuHolder implements InventoryHolder {

    public enum ViewType {
        ROOT,
        REVIEW_BUCKET_LIST,
        REVIEW_BUCKET_DETAIL,
        RULE_IMPACT_LIST,
        RULE_IMPACT_DETAIL,
        ITEM_INSPECTOR
    }

    private final AdminCatalogViewState state;
    private final ViewType viewType;
    private final String bucketId;
    private final String ruleId;
    private final String itemKey;
    private final String returnBucketId;
    private final String returnRuleId;
    private Inventory inventory;

    private AdminMenuHolder(
        final AdminCatalogViewState state,
        final ViewType viewType,
        final String bucketId,
        final String ruleId,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.viewType = Objects.requireNonNull(viewType, "viewType");
        this.bucketId = bucketId;
        this.ruleId = ruleId;
        this.itemKey = itemKey;
        this.returnBucketId = returnBucketId;
        this.returnRuleId = returnRuleId;
    }

    public static AdminMenuHolder root(final AdminCatalogViewState state) {
        return new AdminMenuHolder(state, ViewType.ROOT, null, null, null, null, null);
    }

    public static AdminMenuHolder reviewBucketList(final AdminCatalogViewState state) {
        return new AdminMenuHolder(state, ViewType.REVIEW_BUCKET_LIST, null, null, null, null, null);
    }

    public static AdminMenuHolder reviewBucketDetail(final AdminCatalogViewState state, final String bucketId) {
        return new AdminMenuHolder(state, ViewType.REVIEW_BUCKET_DETAIL, bucketId, null, null, null, null);
    }

    public static AdminMenuHolder ruleImpactList(final AdminCatalogViewState state) {
        return new AdminMenuHolder(state, ViewType.RULE_IMPACT_LIST, null, null, null, null, null);
    }

    public static AdminMenuHolder ruleImpactDetail(final AdminCatalogViewState state, final String ruleId) {
        return new AdminMenuHolder(state, ViewType.RULE_IMPACT_DETAIL, null, ruleId, null, null, null);
    }

    public static AdminMenuHolder itemInspector(
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId
    ) {
        return new AdminMenuHolder(state, ViewType.ITEM_INSPECTOR, null, null, itemKey, returnBucketId, returnRuleId);
    }

    public Inventory createInventory(final int size, final String title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        return this.inventory;
    }

    @Override
    public Inventory getInventory() {
        if (this.inventory == null) {
            throw new IllegalStateException("Inventory has not been created for this holder yet");
        }
        return this.inventory;
    }

    public AdminCatalogViewState state() {
        return this.state;
    }

    public ViewType viewType() {
        return this.viewType;
    }

    public String bucketId() {
        return this.bucketId;
    }

    public String ruleId() {
        return this.ruleId;
    }

    public String itemKey() {
        return this.itemKey;
    }

    public String returnBucketId() {
        return this.returnBucketId;
    }

    public String returnRuleId() {
        return this.returnRuleId;
    }
}

