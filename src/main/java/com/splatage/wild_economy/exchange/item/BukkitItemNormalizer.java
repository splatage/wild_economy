package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class BukkitItemNormalizer implements ItemNormalizer {

    @Override
    public Optional<ItemKey> normalizeForExchange(final ItemStack itemStack) {
        return Optional.empty();
    }
}
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class BukkitItemNormalizer implements ItemNormalizer {

    private final CanonicalItemRules canonicalItemRules;

    public BukkitItemNormalizer(final CanonicalItemRules canonicalItemRules) {
        this.canonicalItemRules = canonicalItemRules;
    }

    @Override
    public Optional<ItemKey> normalizeForExchange(final ItemStack itemStack) {
        if (!this.canonicalItemRules.isCanonicalForExchange(itemStack)) {
            return Optional.empty();
        }
        return Optional.of(this.canonicalItemRules.toItemKey(itemStack));
    }
}
