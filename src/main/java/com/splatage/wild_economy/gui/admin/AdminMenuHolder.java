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
        REVIEW_BUCKET_SUBGROUP_DETAIL,
        RULE_IMPACT_LIST,
        RULE_IMPACT_DETAIL,
        RULE_IMPACT_SAMPLE_DETAIL,
        ITEM_INSPECTOR
    }

    private final AdminCatalogViewState state;
    private final ViewType viewType;
    private final String bucketId;
    private final String subgroupId;
    private final String ruleId;
    private final String sampleGroupId;
    private final String itemKey;
    private final String returnBucketId;
    private final String returnRuleId;
    private final int pageIndex;
    private final String sortMode;
    private Inventory inventory;

    private AdminMenuHolder(
        final AdminCatalogViewState state,
        final ViewType viewType,
        final String bucketId,
        final String subgroupId,
        final String ruleId,
        final String sampleGroupId,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId,
        final int pageIndex,
        final String sortMode
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.viewType = Objects.requireNonNull(viewType, "viewType");
        this.bucketId = bucketId;
        this.subgroupId = subgroupId;
        this.ruleId = ruleId;
        this.sampleGroupId = sampleGroupId;
        this.itemKey = itemKey;
        this.returnBucketId = returnBucketId;
        this.returnRuleId = returnRuleId;
        this.pageIndex = Math.max(0, pageIndex);
        this.sortMode = sortMode == null || sortMode.isBlank() ? "default" : sortMode;
    }

    public static AdminMenuHolder root(final AdminCatalogViewState state) {
        return new AdminMenuHolder(state, ViewType.ROOT, null, null, null, null, null, null, null, 0, "default");
    }

    public static AdminMenuHolder reviewBucketList(final AdminCatalogViewState state, final int pageIndex, final String sortMode) {
        return new AdminMenuHolder(state, ViewType.REVIEW_BUCKET_LIST, null, null, null, null, null, null, null, pageIndex, sortMode);
    }

    public static AdminMenuHolder reviewBucketDetail(
        final AdminCatalogViewState state,
        final String bucketId,
        final int pageIndex,
        final String sortMode
    ) {
        return new AdminMenuHolder(state, ViewType.REVIEW_BUCKET_DETAIL, bucketId, null, null, null, null, null, null, pageIndex, sortMode);
    }

    public static AdminMenuHolder reviewBucketSubgroupDetail(
        final AdminCatalogViewState state,
        final String bucketId,
        final String subgroupId,
        final int pageIndex,
        final String sortMode
    ) {
        return new AdminMenuHolder(state, ViewType.REVIEW_BUCKET_SUBGROUP_DETAIL, bucketId, subgroupId, null, null, null, null, null, pageIndex, sortMode);
    }

    public static AdminMenuHolder ruleImpactList(final AdminCatalogViewState state, final int pageIndex, final String sortMode) {
        return new AdminMenuHolder(state, ViewType.RULE_IMPACT_LIST, null, null, null, null, null, null, null, pageIndex, sortMode);
    }

    public static AdminMenuHolder ruleImpactDetail(
        final AdminCatalogViewState state,
        final String ruleId,
        final int pageIndex,
        final String sortMode
    ) {
        return new AdminMenuHolder(state, ViewType.RULE_IMPACT_DETAIL, null, null, ruleId, null, null, null, null, pageIndex, sortMode);
    }

    public static AdminMenuHolder ruleImpactSampleDetail(
        final AdminCatalogViewState state,
        final String ruleId,
        final String sampleGroupId,
        final int pageIndex,
        final String sortMode
    ) {
        return new AdminMenuHolder(state, ViewType.RULE_IMPACT_SAMPLE_DETAIL, null, null, ruleId, sampleGroupId, null, null, null, pageIndex, sortMode);
    }

    public static AdminMenuHolder itemInspector(
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId,
        final int pageIndex,
        final String sortMode
    ) {
        return new AdminMenuHolder(state, ViewType.ITEM_INSPECTOR, null, null, null, null, itemKey, returnBucketId, returnRuleId, pageIndex, sortMode);
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

    public AdminCatalogViewState state() { return this.state; }
    public ViewType viewType() { return this.viewType; }
    public String bucketId() { return this.bucketId; }
    public String subgroupId() { return this.subgroupId; }
    public String ruleId() { return this.ruleId; }
    public String sampleGroupId() { return this.sampleGroupId; }
    public String itemKey() { return this.itemKey; }
    public String returnBucketId() { return this.returnBucketId; }
    public String returnRuleId() { return this.returnRuleId; }
    public int pageIndex() { return this.pageIndex; }
    public String sortMode() { return this.sortMode; }
}

