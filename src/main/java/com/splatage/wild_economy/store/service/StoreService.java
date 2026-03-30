package com.splatage.wild_economy.store.service;

import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StorePurchaseResult;
import com.splatage.wild_economy.store.state.StoreOwnershipState;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;

public interface StoreService {
    List<StoreCategory> getCategories();
    List<StoreCategory> getVisibleCategories(Player player);
    List<StoreProduct> getProducts(String categoryId);
    List<StoreProduct> getVisibleProducts(Player player, String categoryId);
    StoreEligibilityResult getCategoryEligibility(Player player, String categoryId);
    StoreEligibilityResult getProductEligibility(Player player, String productId);
    void ensurePlayerLoadedAsync(UUID playerId);
    StoreOwnershipState getOwnershipState(UUID playerId, String entitlementKey);
    StorePurchaseResult purchase(Player player, String productId);
}
