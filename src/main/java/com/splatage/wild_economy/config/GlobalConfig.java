package com.splatage.wild_economy.config;

public record GlobalConfig(
    long turnoverIntervalTicks,
    int guiPageSize,
    String baseCommand,
    String adminCommand,
    boolean debugLogging
) {}
