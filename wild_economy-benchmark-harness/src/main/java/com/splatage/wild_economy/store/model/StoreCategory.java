package com.splatage.wild_economy.store.model;

import java.util.Objects;

public record StoreCategory(
    String categoryId,
    String displayName,
    String iconKey,
    int slot
) {
    public StoreCategory {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        if (iconKey == null || iconKey.isBlank()) {
            throw new IllegalArgumentException("iconKey cannot be blank");
        }
        if (slot < 0) {
            throw new IllegalArgumentException("slot cannot be negative");
        }
        Objects.requireNonNull(categoryId, "categoryId");
    }
}
