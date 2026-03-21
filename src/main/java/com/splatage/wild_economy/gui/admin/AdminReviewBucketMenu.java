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

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void openList(final Player player, final AdminCatalogViewState state) {
        final AdminMenuHolder holder = AdminMenuHolder.reviewBucketList(state);
        final Inventory inventory = holder.createInventory(54, "Admin - Review Buckets");
        final List<AdminCatalogReviewBucket> buckets = this.sortedBuckets(state);

        int slot = 0;
        for (final AdminCatalogReviewBucket bucket : buckets) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.bucketItem(bucket));
            slot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.button(Material.BOOK, "Refresh Root"));

        player.openInventory(inventory);
    }

    public void openDetail(final Player player, final AdminCatalogViewState state, final String bucketId) {
        final AdminCatalogReviewBucket bucket = state.findReviewBucket(bucketId);
        if (bucket == null) {
            player.sendMessage("Unknown review bucket: " + bucketId);
            this.openList(player, state);
            return;
        }

        final AdminMenuHolder holder = AdminMenuHolder.reviewBucketDetail(state, bucketId);
        final Inventory inventory = holder.createInventory(54, "Bucket - " + this.displayBucketId(bucket.bucketId()));

        inventory.setItem(4, this.bucketSummaryItem(bucket));

        final List<Map.Entry<String, Integer>> subgroups = bucket.subgroupCounts().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(9)
            .toList();
        int subgroupSlot = 9;
        for (final Map.Entry<String, Integer> entry : subgroups) {
            inventory.setItem(subgroupSlot, this.subgroupItem(entry.getKey(), entry.getValue(), bucket.subgroupSampleItems()));
            subgroupSlot++;
        }

        int sampleSlot = 18;
        for (final String itemKey : bucket.sampleItems()) {
            if (sampleSlot >= 45) {
                break;
            }
            inventory.setItem(sampleSlot, this.itemButton(itemKey, "Open inspector"));
            sampleSlot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.CHEST, "Bucket List"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void openSubgroupDetail(
        final Player player,
        final AdminCatalogViewState state,
        final String bucketId,
        final String subgroupId
    ) {
        final AdminCatalogReviewBucket bucket = state.findReviewBucket(bucketId);
        if (bucket == null) {
            player.sendMessage("Unknown review bucket: " + bucketId);
            this.openList(player, state);
            return;
        }
        if (!bucket.subgroupCounts().containsKey(subgroupId)) {
            player.sendMessage("Unknown review bucket subgroup: " + subgroupId);
            this.openDetail(player, state, bucketId);
            return;
        }

        final AdminMenuHolder holder = AdminMenuHolder.reviewBucketSubgroupDetail(state, bucketId, subgroupId);
        final Inventory inventory = holder.createInventory(54, "Subgroup - " + subgroupId);

        inventory.setItem(4, this.subgroupSummaryItem(bucket, subgroupId));

        int sampleSlot = 18;
        for (final String itemKey : bucket.subgroupSampleItems().getOrDefault(subgroupId, List.of())) {
            if (sampleSlot >= 45) {
                break;
            }
            inventory.setItem(sampleSlot, this.itemButton(itemKey, "Open inspector"));
            sampleSlot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.CHEST, "Bucket Detail"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (holder.viewType()) {
            case REVIEW_BUCKET_LIST -> this.handleListClick(event, player, holder.state());
            case REVIEW_BUCKET_DETAIL -> this.handleDetailClick(event, player, holder);
            case REVIEW_BUCKET_SUBGROUP_DETAIL -> this.handleSubgroupDetailClick(event, player, holder);
            default -> {
            }
        }
    }

    private void handleListClick(final InventoryClickEvent event, final Player player, final AdminCatalogViewState state) {
        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final List<AdminCatalogReviewBucket> buckets = this.sortedBuckets(state);
            if (slot < buckets.size()) {
                this.adminMenuRouter.openReviewBucketDetail(player, state, buckets.get(slot).bucketId());
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
        final AdminCatalogReviewBucket bucket = holder.state().findReviewBucket(holder.bucketId());
        if (bucket == null) {
            this.adminMenuRouter.openReviewBucketList(player, holder.state());
            return;
        }

        if (slot >= 9 && slot < 18) {
            final int index = slot - 9;
            final List<Map.Entry<String, Integer>> subgroups = bucket.subgroupCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(9)
                .toList();
            if (index < subgroups.size()) {
                final String subgroupId = subgroups.get(index).getKey();
                this.adminMenuRouter.openReviewBucketSubgroupDetail(
                    player,
                    holder.state(),
                    bucket.bucketId(),
                    subgroupId
                );
            }
            return;
        }

        if (slot >= 18 && slot < 45) {
            final int index = slot - 18;
            if (index < bucket.sampleItems().size()) {
                this.adminMenuRouter.openItemInspector(
                    player,
                    holder.state(),
                    bucket.sampleItems().get(index),
                    bucket.bucketId(),
                    null
                );
            }
            return;
        }

        switch (slot) {
            case 45, 49 -> this.adminMenuRouter.openReviewBucketList(player, holder.state());
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleSubgroupDetailClick(
        final InventoryClickEvent event,
        final Player player,
        final AdminMenuHolder holder
    ) {
        final int slot = event.getRawSlot();
        final AdminCatalogReviewBucket bucket = holder.state().findReviewBucket(holder.bucketId());
        if (bucket == null || holder.subgroupId() == null) {
            this.adminMenuRouter.openReviewBucketList(player, holder.state());
            return;
        }

        final List<String> subgroupItems = bucket.subgroupSampleItems().getOrDefault(holder.subgroupId(), List.of());

        if (slot >= 18 && slot < 45) {
            final int index = slot - 18;
            if (index < subgroupItems.size()) {
                this.adminMenuRouter.openItemInspector(
                    player,
                    holder.state(),
                    subgroupItems.get(index),
                    bucket.bucketId(),
                    null
                );
            }
            return;
        }

        switch (slot) {
            case 45, 49 -> this.adminMenuRouter.openReviewBucketDetail(player, holder.state(), bucket.bucketId());
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private List<AdminCatalogReviewBucket> sortedBuckets(final AdminCatalogViewState state) {
        final List<AdminCatalogReviewBucket> buckets = new ArrayList<>(state.reviewBuckets());
        buckets.sort(Comparator.comparingInt(AdminCatalogReviewBucket::count).reversed());
        return buckets;
    }

    private ItemStack bucketItem(final AdminCatalogReviewBucket bucket) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Count: " + bucket.count());
        for (final Map.Entry<String, Integer> entry : bucket.subgroupCounts().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(4)
            .toList()) {
            lore.add(entry.getKey() + ": " + entry.getValue());
        }
        lore.add("Click to open bucket detail.");
        return this.item(Material.CHEST, this.displayBucketId(bucket.bucketId()), lore);
    }

    private ItemStack bucketSummaryItem(final AdminCatalogReviewBucket bucket) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Total items: " + bucket.count());
        if (!bucket.subgroupCounts().isEmpty()) {
            final Map.Entry<String, Integer> topSubgroup = bucket.subgroupCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .findFirst()
                .orElse(null);
            if (topSubgroup != null) {
                lore.add("Top subgroup: " + topSubgroup.getKey() + " (" + topSubgroup.getValue() + ")");
            }
        }
        lore.add("Subgroup buttons open subgroup detail.");
        lore.add("Item buttons inspect direct sample items.");
        return this.item(Material.ENDER_CHEST, this.displayBucketId(bucket.bucketId()), lore);
    }

    private ItemStack subgroupSummaryItem(final AdminCatalogReviewBucket bucket, final String subgroupId) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Subgroup: " + subgroupId);
        lore.add("Count: " + bucket.subgroupCounts().getOrDefault(subgroupId, Integer.valueOf(0)));
        final List<String> sample = bucket.subgroupSampleItems().getOrDefault(subgroupId, List.of());
        if (sample.isEmpty()) {
            lore.add("No subgroup sample items.");
        } else {
            lore.add("Showing subgroup sample items.");
        }
        lore.add("Click an item below to inspect it.");
        return this.item(Material.ENDER_CHEST, subgroupId, lore);
    }

    private ItemStack subgroupItem(
        final String subgroupId,
        final int count,
        final Map<String, List<String>> subgroupSampleItems
    ) {
        final List<String> lore = new ArrayList<>();
        lore.add("Count: " + count);
        final List<String> sample = subgroupSampleItems.getOrDefault(subgroupId, List.of());
        for (final String itemKey : sample.stream().limit(4).toList()) {
            lore.add(itemKey);
        }
        lore.add(sample.isEmpty() ? "No subgroup sample items." : "Click to open subgroup detail.");
        return this.item(Material.PAPER, subgroupId, lore);
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

    private String displayBucketId(final String bucketId) {
        return bucketId.replace('-', ' ');
    }
}

