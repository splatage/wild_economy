package com.splatage.wild_economy.catalog.admin;

public record AdminCatalogEcoEnvelope(
    String name,
    double buyPriceAtMinStockMultiplier,
    double buyPriceAtMaxStockMultiplier,
    double sellPriceAtMinStockMultiplier,
    double sellPriceAtMaxStockMultiplier
) { }
