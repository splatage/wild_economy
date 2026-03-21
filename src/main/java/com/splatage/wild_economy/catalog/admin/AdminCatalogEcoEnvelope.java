package com.splatage.wild_economy.catalog.admin;

import java.util.List;

public record AdminCatalogEcoEnvelope(
    String name,
    double baseBuyMultiplier,
    double baseSellMultiplier,
    List<AdminCatalogSellBand> sellBands
) {
    public AdminCatalogEcoEnvelope {
        sellBands = List.copyOf(sellBands);
    }
}
