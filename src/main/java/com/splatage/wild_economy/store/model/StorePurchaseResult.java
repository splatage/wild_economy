package com.splatage.wild_economy.store.model;

import com.splatage.wild_economy.economy.model.MoneyAmount;

public record StorePurchaseResult(
    boolean success,
    String message,
    StoreProduct product,
    MoneyAmount resultingBalance
) {
    public static StorePurchaseResult success(final StoreProduct product, final MoneyAmount resultingBalance) {
        return new StorePurchaseResult(true, null, product, resultingBalance);
    }

    public static StorePurchaseResult failure(
        final String message,
        final StoreProduct product,
        final MoneyAmount resultingBalance
    ) {
        return new StorePurchaseResult(false, message, product, resultingBalance);
    }
}
