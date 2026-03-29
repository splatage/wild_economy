package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogReviewBucket;
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

public final class AdminReviewBucketMenu {

    private static final int ITEMS_PER_PAGE = 36;
    private static final int SLOT_PREV = 46;
    private static final int SLOT_NEXT = 47;
    private static final int SLOT_SORT = 48;

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void openList(final Player player, final AdminCatalogViewState state, final int pageIndex, final String sortMode) {
        final List<AdminCatalogReviewBucket> buckets = this.sortedBuckets(state, sortMode);
        final int pageCount = Math.max(1, (int) Math.ceil((double) buckets.size() / ITEMS_PER_PAGE));
        final int safePage = Math.max(0, Math.min(pageIndex, pageCount - 1));
        final AdminMenuHolder holder = AdminMenuHolder.reviewBucketList(state, safePage, sortMode);
        final Inventory inventory = holder.createInventory(54, "Admin - Review Buckets");

        int slot = 0;
        for (final AdminCatalogReviewBucket bucket : this.pageSlice(buckets, safePage)) {
            inventory.setItem(slot++, this.bucketItem(bucket));
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

    public void openDetail(final Player player, final AdminCatalogViewState state, final String bucketId, final int pageIndex, final String sortMode) {
        final AdminCatalogReviewBucket bucket = state.findReviewBucket(bucketId);
        if (bucket == null) {
            player.sendMessage("Unknown review bucket: " + bucketId);
            this.openList(player, state, pageIndex, sortMode);
            return;
        }
        final AdminMenuHolder holder = AdminMenuHolder.reviewBucketDetail(state, bucketId, pageIndex, sortMode);
        final Inventory inventory = holder.createInventory(54, "Bucket - " + this.displayBucketId(bucket.bucketId()));
        inventory.setItem(4, this.bucketSummaryItem(bucket));
        inventory.setItem(8, this.bucketContextItem(bucket, sortMode));

        final List<Map.Entry<String, Integer>> subgroups = bucket.subgroupCounts().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(9).toList();
        int subgroupSlot = 9;
        for (final Map.Entry<String, Integer> entry : subgroups) {
            inventory.setItem(subgroupSlot++, this.subgroupItem(entry.getKey(), entry.getValue(), bucket.subgroupSampleItems()));
        }

        int sampleSlot = 18;
        for (final String itemKey : bucket.sampleItems()) {
            if (sampleSlot >= 45) break;
            inventory.setItem(sampleSlot++, this.itemButton(itemKey, "Open inspector"));
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.CHEST, "Bucket List"));
        inventory.setItem(50, this.button(Material.COMPASS, "Root"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void openSubgroupDetail(final Player player, final AdminCatalogViewState state, final String bucketId, final String subgroupId, final int pageIndex, final String sortMode) {
        final AdminCatalogReviewBucket bucket = state.findReviewBucket(bucketId);
        if (bucket == null) {
            player.sendMessage("Unknown review bucket: " + bucketId);
            this.openList(player, state, pageIndex, sortMode);
            return;
        }
        if (!bucket.subgroupCounts().containsKey(subgroupId)) {
            player.sendMessage("Unknown review bucket subgroup: " + subgroupId);
            this.openDetail(player, state, bucketId, pageIndex, sortMode);
            return;
        }
        final AdminMenuHolder holder = AdminMenuHolder.reviewBucketSubgroupDetail(state, bucketId, subgroupId, pageIndex, sortMode);
        final Inventory inventory = holder.createInventory(54, "Subgroup - " + subgroupId);
        inventory.setItem(4, this.subgroupSummaryItem(bucket, subgroupId));
        inventory.setItem(8, this.bucketContextItem(bucket, sortMode));

        int slot = 18;
        for (final String itemKey : bucket.subgroupSampleItems().getOrDefault(subgroupId, List.of())) {
            if (slot >= 45) break;
            inventory.setItem(slot++, this.itemButton(itemKey, "Open inspector"));
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.CHEST, "Bucket Detail"));
        inventory.setItem(50, this.button(Material.COMPASS, "Root"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        switch (holder.viewType()) {
            case REVIEW_BUCKET_LIST -> this.handleListClick(event, player, holder);
            case REVIEW_BUCKET_DETAIL -> this.handleDetailClick(event, player, holder);
            case REVIEW_BUCKET_SUBGROUP_DETAIL -> this.handleSubgroupDetailClick(event, player, holder);
            default -> { }
        }
    }

    private void handleListClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final List<AdminCatalogReviewBucket> buckets = this.sortedBuckets(holder.state(), holder.sortMode());
        final List<AdminCatalogReviewBucket> page = this.pageSlice(buckets, holder.pageIndex());
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            if (slot < page.size()) {
                this.adminMenuRouter.openReviewBucketDetail(player, holder.state(), page.get(slot).bucketId(), holder.pageIndex(), holder.sortMode());
            }
            return;
        }
        switch (slot) {
            case 45 -> this.adminMenuRouter.openRoot(player, holder.state());
            case SLOT_PREV -> { if (holder.pageIndex() > 0) this.adminMenuRouter.openReviewBucketList(player, holder.state(), holder.pageIndex() - 1, holder.sortMode()); }
            case SLOT_NEXT -> {
                final int pageCount = Math.max(1, (int) Math.ceil((double) buckets.size() / ITEMS_PER_PAGE));
                if (holder.pageIndex() + 1 < pageCount) this.adminMenuRouter.openReviewBucketList(player, holder.state(), holder.pageIndex() + 1, holder.sortMode());
            }
            case SLOT_SORT -> this.adminMenuRouter.openReviewBucketList(player, holder.state(), 0, this.toggleSort(holder.sortMode()));
            case 50 -> this.adminMenuRouter.openRoot(player, holder.state());
            case 53 -> player.closeInventory();
            default -> { }
        }
    }

    private void handleDetailClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final AdminCatalogReviewBucket bucket = holder.state().findReviewBucket(holder.bucketId());
        if (bucket == null) {
            this.adminMenuRouter.openReviewBucketList(player, holder.state(), holder.pageIndex(), holder.sortMode());
            return;
        }
        if (slot >= 9 && slot < 18) {
            final int index = slot - 9;
            final List<Map.Entry<String, Integer>> subgroups = bucket.subgroupCounts().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(9).toList();
            if (index < subgroups.size()) {
                this.adminMenuRouter.openReviewBucketSubgroupDetail(player, holder.state(), bucket.bucketId(), subgroups.get(index).getKey(), holder.pageIndex(), holder.sortMode());
            }
            return;
        }
        if (slot >= 18 && slot < 45) {
            final int index = slot - 18;
            if (index < bucket.sampleItems().size()) {
                this.adminMenuRouter.openItemInspector(player, holder.state(), bucket.sampleItems().get(index), bucket.bucketId(), null, holder.pageIndex(), holder.sortMode());
            }
            return;
        }
        switch (slot) {
            case 45, 49 -> this.adminMenuRouter.openReviewBucketList(player, holder.state(), holder.pageIndex(), holder.sortMode());
            case 50 -> this.adminMenuRouter.openRoot(player, holder.state());
            case 53 -> player.closeInventory();
            default -> { }
        }
    }

    private void handleSubgroupDetailClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final AdminCatalogReviewBucket bucket = holder.state().findReviewBucket(holder.bucketId());
        if (bucket == null || holder.subgroupId() == null) {
            this.adminMenuRouter.openReviewBucketList(player, holder.state(), holder.pageIndex(), holder.sortMode());
            return;
        }
        final List<String> subgroupItems = bucket.subgroupSampleItems().getOrDefault(holder.subgroupId(), List.of());
        if (slot >= 18 && slot < 45) {
            final int index = slot - 18;
            if (index < subgroupItems.size()) {
                this.adminMenuRouter.openItemInspector(player, holder.state(), subgroupItems.get(index), bucket.bucketId(), null, holder.pageIndex(), holder.sortMode());
            }
            return;
        }
        switch (slot) {
            case 45, 49 -> this.adminMenuRouter.openReviewBucketDetail(player, holder.state(), bucket.bucketId(), holder.pageIndex(), holder.sortMode());
            case 50 -> this.adminMenuRouter.openRoot(player, holder.state());
            case 53 -> player.closeInventory();
            default -> { }
        }
    }

    private List<AdminCatalogReviewBucket> sortedBuckets(final AdminCatalogViewState state, final String sortMode) {
        final List<AdminCatalogReviewBucket> buckets = new ArrayList<>(state.reviewBuckets());
        if ("name".equalsIgnoreCase(sortMode)) {
            buckets.sort(Comparator.comparing(AdminCatalogReviewBucket::bucketId));
        } else {
            buckets.sort(Comparator.comparingInt(AdminCatalogReviewBucket::count).reversed().thenComparing(AdminCatalogReviewBucket::bucketId));
        }
        return buckets;
    }

    private List<AdminCatalogReviewBucket> pageSlice(final List<AdminCatalogReviewBucket> buckets, final int pageIndex) {
        final int from = Math.max(0, pageIndex) * ITEMS_PER_PAGE;
        if (from >= buckets.size()) return List.of();
        final int to = Math.min(buckets.size(), from + ITEMS_PER_PAGE);
        return buckets.subList(from, to);
    }

    private String toggleSort(final String sortMode) { return "name".equalsIgnoreCase(sortMode) ? "count" : "name"; }
    private String prettySort(final String sortMode) { return "name".equalsIgnoreCase(sortMode) ? "name" : "count"; }

    private ItemStack bucketItem(final AdminCatalogReviewBucket bucket) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Count: " + bucket.count());
        for (final Map.Entry<String, Integer> entry : bucket.subgroupCounts().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(4).toList()) {
            lore.add(entry.getKey() + ": " + entry.getValue());
        }
        lore.add("Click to open bucket detail.");
        return this.item(Material.CHEST, this.displayBucketId(bucket.bucketId()), lore);
    }

    private ItemStack bucketSummaryItem(final AdminCatalogReviewBucket bucket) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Total items: " + bucket.count());
        final Map.Entry<String, Integer> top = bucket.subgroupCounts().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).findFirst().orElse(null);
        if (top != null) lore.add("Top subgroup: " + top.getKey() + " (" + top.getValue() + ")");
        lore.add("Subgroup buttons open subgroup detail.");
        lore.add("Item buttons inspect direct sample items.");
        return this.item(Material.ENDER_CHEST, this.displayBucketId(bucket.bucketId()), lore);
    }

    private ItemStack bucketContextItem(final AdminCatalogReviewBucket bucket, final String sortMode) {
        final List<String> lore = new ArrayList<>();
        lore.add("Subgroups: " + bucket.subgroupCounts().size());
        lore.add("Direct sample items: " + bucket.sampleItems().size());
        lore.add("List sort: " + this.prettySort(sortMode));
        if (bucket.subgroupCounts().size() > 9) lore.add("Showing top 9 subgroups here.");
        return this.item(Material.BOOK, "Bucket Context", lore);
    }

    private ItemStack subgroupSummaryItem(final AdminCatalogReviewBucket bucket, final String subgroupId) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Subgroup: " + subgroupId);
        lore.add("Count: " + bucket.subgroupCounts().getOrDefault(subgroupId, Integer.valueOf(0)));
        final List<String> sample = bucket.subgroupSampleItems().getOrDefault(subgroupId, List.of());
        lore.add(sample.isEmpty() ? "No subgroup sample items." : "Showing subgroup sample items.");
        lore.add("Click an item below to inspect it.");
        return this.item(Material.ENDER_CHEST, subgroupId, lore);
    }

    private ItemStack subgroupItem(final String subgroupId, final int count, final Map<String, List<String>> subgroupSampleItems) {
        final List<String> lore = new ArrayList<>();
        lore.add("Count: " + count);
        final List<String> sample = subgroupSampleItems.getOrDefault(subgroupId, List.of());
        for (final String itemKey : sample.stream().limit(4).toList()) lore.add(itemKey);
        lore.add(sample.isEmpty() ? "No subgroup sample items." : "Click to open subgroup detail.");
        return this.item(Material.PAPER, subgroupId, lore);
    }

    private ItemStack itemButton(final String itemKey, final String footer) {
        final Material material = this.resolveMaterial(itemKey);
        return this.item(material == null ? Material.BARRIER : material, this.displayItemKey(itemKey), List.of(itemKey, footer));
    }

    private ItemStack navButton(final Material material, final String name, final boolean enabled) {
        return this.item(enabled ? material : Material.GRAY_STAINED_GLASS_PANE, name, enabled ? List.of() : List.of("Unavailable"));
    }

    private ItemStack sortButton(final String sortMode) { return this.item(Material.HOPPER, "Sort", List.of("Current: " + this.prettySort(sortMode), "Click to toggle.")); }
    private ItemStack button(final Material material, final String name) { return this.item(material, name, List.of()); }
    private ItemStack item(final Material material, final String name, final List<String> lore) { final ItemStack stack = new ItemStack(material); final ItemMeta meta = stack.getItemMeta(); if (meta != null) { meta.setDisplayName(name); if (!lore.isEmpty()) meta.setLore(lore); stack.setItemMeta(meta);} return stack; }
    private Material resolveMaterial(final String itemKey) { return Material.matchMaterial(itemKey.replace("minecraft:", "").toUpperCase(Locale.ROOT)); }
    private String displayItemKey(final String itemKey) { return itemKey.replace("minecraft:", "").replace('_', ' '); }
    private String displayBucketId(final String bucketId) { return bucketId.replace('-', ' '); }
}

