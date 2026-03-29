package com.splatage.wild_economy.store.state;

public record DirtyEntitlementGrant(
    String entitlementKey,
    String productId,
    long grantedAtEpochSecond
) {
}
