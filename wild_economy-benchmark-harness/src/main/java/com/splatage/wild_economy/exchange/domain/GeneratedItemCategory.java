package com.splatage.wild_economy.exchange.domain;

public enum GeneratedItemCategory {
    FARMING("Farming"),
    FOOD("Food"),
    ORES_AND_MINERALS("Ores & Minerals"),
    MOB_DROPS("Mob Drops"),
    WOODS("Woods"),
    STONE("Stone"),
    REDSTONE("Redstone"),
    TOOLS("Tools"),
    BREWING("Brewing"),
    TRANSPORT("Transport"),
    COMBAT("Combat"),
    NETHER("Nether"),
    END("End"),
    DECORATION("Decoration"),
    MISC("Misc");

    private final String displayName;

    GeneratedItemCategory(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
