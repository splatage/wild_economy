package com.splatage.wild_economy.store.model;

import java.util.Objects;

public record StoreAction(
    StoreActionType type,
    String value
) {
    public StoreAction {
        Objects.requireNonNull(type, "type");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Store action value cannot be blank");
        }
    }
}
