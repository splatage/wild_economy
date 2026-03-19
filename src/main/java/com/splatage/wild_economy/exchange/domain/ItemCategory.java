package com.splatage.wild_economy.exchange.domain;

public enum ItemCategory {
    FARMING_AND_FOOD("Farming & Food"),
    MINING_AND_MINERALS("Mining & Minerals"),
    MOB_DROPS("Mob Drops"),
    BUILDING_MATERIALS("Building Materials"),
    REDSTONE_AND_UTILITY("Redstone & Utility"),
    COMBAT_AND_ADVENTURE("Combat & Adventure"),
    MISC("Misc");

    private final String displayName;

    ItemCategory(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
