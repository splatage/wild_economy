package com.splatage.wild_economy.store.action;

public record StoreActionExecutionResult(
    boolean success,
    String message
) {
    public static StoreActionExecutionResult success() {
        return new StoreActionExecutionResult(true, null);
    }

    public static StoreActionExecutionResult failure(final String message) {
        return new StoreActionExecutionResult(false, message);
    }
}
