package com.splatage.wild_economy.store.eligibility;

import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import org.bukkit.entity.Player;

public interface StoreEligibilityService {
    StoreEligibilityResult evaluateCategory(Player player, StoreCategory category);
    StoreEligibilityResult evaluateProduct(Player player, StoreProduct product);
}
