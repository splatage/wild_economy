package com.splatage.wild_economy.integration.protection;

public record ContainerAccessResult(boolean allowed, String message) {

    public static ContainerAccessResult allow() {
        return new ContainerAccessResult(true, "");
    }

    public static ContainerAccessResult deny(final String message) {
        return new ContainerAccessResult(false, message);
    }
}
