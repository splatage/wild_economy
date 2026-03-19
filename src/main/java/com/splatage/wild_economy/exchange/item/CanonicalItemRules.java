package com.splatage.wild_economy.exchange.item;

public final class CanonicalItemRules {
}
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class CanonicalItemRules {

    public boolean isCanonicalForExchange(final ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        if (itemStack.getType() == Material.AIR) {
            return false;
        }
        if (itemStack.getAmount() <= 0) {
            return false;
        }
        return !this.hasForbiddenMeta(itemStack);
    }

    public ItemKey toItemKey(final ItemStack itemStack) {
        return new ItemKey("minecraft:" + itemStack.getType().name().toLowerCase());
    }

    private boolean hasForbiddenMeta(final ItemStack itemStack) {
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.hasDisplayName()) {
            return true;
        }
        if (meta.hasLore()) {
            return true;
        }
        if (meta.hasEnchants()) {
            return true;
        }
        if (meta.isUnbreakable()) {
            return true;
        }
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            return true;
        }
        return false;
    }
}
