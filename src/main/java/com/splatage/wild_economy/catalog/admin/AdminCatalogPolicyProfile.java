package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.model.CatalogPolicy;

public record AdminCatalogPolicyProfile(
    String id,
    CatalogPolicy policy,
    String runtimePolicy,
    boolean buyEnabled,
    boolean sellEnabled,
    boolean stockBacked,
    boolean unlimitedBuy,
    boolean requiresPlayerStockToBuy,
    String defaultStockProfile,
    String defaultEcoEnvelope,
    String description
) {

    public String behaviorSummary() {
        return this.runtimePolicy()
            + ", buy=" + this.buyEnabled()
            + ", sell=" + this.sellEnabled()
            + ", stock-backed=" + this.stockBacked()
            + ", unlimited-buy=" + this.unlimitedBuy();
    }
}

