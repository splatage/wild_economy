package com.splatage.wild_economy.exchange.domain;

public enum RejectionReason {
    ITEM_NOT_ELIGIBLE,
    ITEM_DISABLED,
    SELL_NOT_ALLOWED,
    BUY_NOT_ALLOWED,
    STOCK_FULL,
    OUT_OF_STOCK,
    INSUFFICIENT_FUNDS,
    INVALID_ITEM_STATE,
    INVENTORY_FULL,
    INTERNAL_ERROR
}
