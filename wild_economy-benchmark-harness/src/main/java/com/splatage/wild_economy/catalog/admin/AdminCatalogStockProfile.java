package com.splatage.wild_economy.catalog.admin;

public record AdminCatalogStockProfile(
    String name,
    int stockCap,
    int turnoverAmountPerInterval,
    int lowStockThreshold,
    int overflowThreshold
) { }
