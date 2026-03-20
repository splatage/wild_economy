# wild_economy patch: protect filled shulkers from being sold as items

Scope:

* prevent filled shulker boxes from being treated as canonical Exchange items
* keep empty shulker boxes sellable if they are otherwise valid and catalog-enabled
* leave `/sellcontainer` behavior intact for intentionally selling shulker contents

Rationale:

* the live repo currently rejects only display name, lore, enchants, unbreakable, and damage in `CanonicalItemRules`, so filled shulkers can still normalize and be sold as shulker items today. ([raw.githubusercontent.com](https://raw.githubusercontent.com/splatage/wild_economy/main/src/main/java/com/splatage/wild_economy/exchange/item/CanonicalItemRules.java))
* `validateForSell(...)` depends on the normalizer/canonical rules, so fixing this at the canonical layer protects `sellHand()` and `sellAll()` without duplicating logic in each command path. ([raw.githubusercontent.com](https://raw.githubusercontent.com/splatage/wild_economy/main/src/main/java/com/splatage/wild_economy/exchange/item/BukkitItemNormalizer.java))

---

## File: `src/main/java/com/splatage/wild_economy/exchange/item/CanonicalItemRules.java`

```java
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
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
        if (meta instanceof BlockStateMeta blockStateMeta
            && blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox
            && this.hasAnyContents(shulkerBox)) {
            return true;
        }
        return false;
    }

    private boolean hasAnyContents(final ShulkerBox shulkerBox) {
        for (final ItemStack contents : shulkerBox.getInventory().getContents()) {
            if (contents == null) {
                continue;
            }
            if (contents.getType() == Material.AIR) {
                continue;
            }
            if (contents.getAmount() <= 0) {
                continue;
            }
            return true;
        }
        return false;
    }
}
```
