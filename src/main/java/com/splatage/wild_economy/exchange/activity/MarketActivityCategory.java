package com.splatage.wild_economy.exchange.activity;

public enum MarketActivityCategory {
    RECENTLY_STOCKED("Recently Stocked"),
    RECENTLY_PURCHASED("Recently Purchased"),
    TOP_TURNOVER("Top Turnover"),
    YOUR_RECENT_PURCHASES("Your Recent Purchases");

    private final String displayName;

    MarketActivityCategory(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
