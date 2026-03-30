package com.splatage.wild_economy.store.model;

import com.splatage.wild_economy.economy.model.MoneyAmount;
import java.util.List;
import java.util.Objects;

public record StoreProduct(
    String productId,
    String categoryId,
    StoreProductType type,
    String displayName,
    String iconKey,
    MoneyAmount price,
    String entitlementKey,
    boolean requireConfirmation,
    List<String> lore,
    List<StoreAction> actions,
    int xpCostPoints,
    List<StoreRequirement> requirements,
    StoreVisibilityWhenUnmet visibilityWhenUnmet,
    String lockedMessage
) {
    public StoreProduct {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId cannot be blank");
        }
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId cannot be blank");
        }
        Objects.requireNonNull(type, "type");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        if (iconKey == null || iconKey.isBlank()) {
            throw new IllegalArgumentException("iconKey cannot be blank");
        }
        Objects.requireNonNull(price, "price");
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        visibilityWhenUnmet = Objects.requireNonNull(visibilityWhenUnmet, "visibilityWhenUnmet");

        if (type == StoreProductType.PERMANENT_UNLOCK
                && (entitlementKey == null || entitlementKey.isBlank())) {
            throw new IllegalArgumentException("Permanent unlock products require an entitlement key");
        }

        if (type == StoreProductType.XP_WITHDRAWAL) {
            if (xpCostPoints <= 0) {
                throw new IllegalArgumentException("XP withdrawal products require positive xpCostPoints");
            }
            if (!price.isZero()) {
                throw new IllegalArgumentException("XP withdrawal products must not have a money price");
            }
            if (!actions.isEmpty()) {
                throw new IllegalArgumentException("XP withdrawal products must not define Store actions");
            }
        } else {
            if (xpCostPoints != 0) {
                throw new IllegalArgumentException("Non-XP products must not define xpCostPoints");
            }
            if (actions.isEmpty()) {
                throw new IllegalArgumentException("Non-XP products must define at least one Store action");
            }
        }
    }
}
