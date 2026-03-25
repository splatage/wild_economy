package com.splatage.wild_economy.economy.model;

public record EconomyMutationResult(
    boolean success,
    String message,
    MoneyAmount resultingBalance
) {

    public static EconomyMutationResult success(final MoneyAmount resultingBalance) {
        return new EconomyMutationResult(true, null, resultingBalance);
    }

    public static EconomyMutationResult failure(final String message, final MoneyAmount resultingBalance) {
        return new EconomyMutationResult(false, message, resultingBalance);
    }
}
