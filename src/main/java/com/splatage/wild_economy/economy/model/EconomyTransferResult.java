package com.splatage.wild_economy.economy.model;

public record EconomyTransferResult(
    boolean success,
    String message,
    MoneyAmount senderBalance,
    MoneyAmount recipientBalance
) {

    public static EconomyTransferResult success(
        final MoneyAmount senderBalance,
        final MoneyAmount recipientBalance
    ) {
        return new EconomyTransferResult(true, null, senderBalance, recipientBalance);
    }

    public static EconomyTransferResult failure(
        final String message,
        final MoneyAmount senderBalance,
        final MoneyAmount recipientBalance
    ) {
        return new EconomyTransferResult(false, message, senderBalance, recipientBalance);
    }
}
