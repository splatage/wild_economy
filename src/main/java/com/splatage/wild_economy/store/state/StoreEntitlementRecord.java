package com.splatage.wild_economy.store.state;

public record StoreEntitlementRecord(
    String entitlementKey,
    String productId,
    long grantedAtEpochSecond
) {
}
