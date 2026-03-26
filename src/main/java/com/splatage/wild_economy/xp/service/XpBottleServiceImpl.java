package com.splatage.wild_economy.xp.service;

import com.splatage.wild_economy.WildEconomyPlugin;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class XpBottleServiceImpl implements XpBottleService {

    private final NamespacedKey xpBottleMarkerKey;
    private final NamespacedKey xpBottlePointsKey;
    private final NamespacedKey xpBottleProductIdKey;

    public XpBottleServiceImpl(final WildEconomyPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.xpBottleMarkerKey = new NamespacedKey(plugin, "xp_bottle_marker");
        this.xpBottlePointsKey = new NamespacedKey(plugin, "xp_bottle_points");
        this.xpBottleProductIdKey = new NamespacedKey(plugin, "xp_bottle_product_id");
    }

    @Override
    public int getCurrentXpPoints(final Player player) {
        return player.calculateTotalExperiencePoints();
    }

    @Override
    public XpBottleWithdrawResult withdrawToBottle(
        final Player player,
        final String productId,
        final String displayName,
        final int xpPoints
    ) {
        Objects.requireNonNull(player, "player");
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        if (xpPoints <= 0) {
            return XpBottleWithdrawResult.failure("XP cost must be positive.", this.getCurrentXpPoints(player));
        }

        final int currentXp = this.getCurrentXpPoints(player);
        if (currentXp < xpPoints) {
            return XpBottleWithdrawResult.failure("You do not have enough XP.", currentXp);
        }

        final int remainingXp = currentXp - xpPoints;
        player.setExperienceLevelAndProgress(remainingXp);

        final ItemStack bottle = new ItemStack(Material.EXPERIENCE_BOTTLE);
        final ItemMeta meta = bottle.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(List.of(
                    "Stored XP: " + xpPoints,
                    "Throw to redeem"
            ));

            final PersistentDataContainer persistentDataContainer = meta.getPersistentDataContainer();
            persistentDataContainer.set(this.xpBottleMarkerKey, PersistentDataType.BYTE, (byte) 1);
            persistentDataContainer.set(this.xpBottlePointsKey, PersistentDataType.INTEGER, xpPoints);
            persistentDataContainer.set(this.xpBottleProductIdKey, PersistentDataType.STRING, productId);
            bottle.setItemMeta(meta);
        }

        final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(bottle);
        for (final ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        return XpBottleWithdrawResult.success(xpPoints, remainingXp);
    }

    @Override
    public boolean isCustomBottle(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.EXPERIENCE_BOTTLE) {
            return false;
        }
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(this.xpBottleMarkerKey, PersistentDataType.BYTE)
                && meta.getPersistentDataContainer().has(this.xpBottlePointsKey, PersistentDataType.INTEGER);
    }

    @Override
    public int getStoredXpPoints(final ItemStack itemStack) {
        if (!this.isCustomBottle(itemStack)) {
            return 0;
        }
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        final Integer stored = meta.getPersistentDataContainer().get(this.xpBottlePointsKey, PersistentDataType.INTEGER);
        return stored == null ? 0 : stored;
    }
}
