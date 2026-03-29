package com.splatage.wild_economy.xp.service;

public record XpBottleWithdrawResult(
    boolean success,
    String message,
    int withdrawnXpPoints,
    int remainingXpPoints
) {
    public static XpBottleWithdrawResult success(final int withdrawnXpPoints, final int remainingXpPoints) {
        return new XpBottleWithdrawResult(true, null, withdrawnXpPoints, remainingXpPoints);
    }

    public static XpBottleWithdrawResult failure(final String message, final int remainingXpPoints) {
        return new XpBottleWithdrawResult(false, message, 0, remainingXpPoints);
    }
}
