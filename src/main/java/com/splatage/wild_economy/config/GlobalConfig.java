package com.splatage.wild_economy.config;

public record GlobalConfig(
    long turnoverIntervalTicks,
    int guiPageSize,
    int recentWindowHours,
    String baseCommand,
    String adminCommand,
    boolean debugLogging,
    boolean buyToHeldShulkerEnabled,
    boolean buyToLookedAtContainerEnabled,
    boolean buyToInventoryEnabled,
    boolean buyDropAtFeetEnabled,
    long tieredTrackPurchaseCooldownSeconds
) {}
