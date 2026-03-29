package com.splatage.wild_economy.testing.seed;

import com.splatage.wild_economy.testing.TestProfile;

public record SeedRunReport(
        int playersSeeded,
        int economyLedgerEntriesSeeded,
        int exchangeCatalogEntries,
        int buyEnabledEntries,
        int sellEnabledEntries,
        int positiveSellQuoteEntries,
        int stockRowsSeeded,
        int stockRowsWithPositiveStock,
        int exchangeTransactionsSeeded,
        int supplierContributionRowsSeeded,
        int storeProductsConfigured,
        int storeEntitlementsSeeded,
        int storePurchasesSeeded,
        long durationMillis
) {
    public String describe() {
        return "Seed report: players=" + this.playersSeeded
                + ", ledgerEntries=" + this.economyLedgerEntriesSeeded
                + ", catalogEntries=" + this.exchangeCatalogEntries
                + ", buyEnabled=" + this.buyEnabledEntries
                + ", sellEnabled=" + this.sellEnabledEntries
                + ", positiveSellQuoteEntries=" + this.positiveSellQuoteEntries
                + ", stockRows=" + this.stockRowsSeeded
                + ", stockedRows=" + this.stockRowsWithPositiveStock
                + ", exchangeTransactions=" + this.exchangeTransactionsSeeded
                + ", supplierContributions=" + this.supplierContributionRowsSeeded
                + ", storeProducts=" + this.storeProductsConfigured
                + ", storeEntitlements=" + this.storeEntitlementsSeeded
                + ", storePurchases=" + this.storePurchasesSeeded
                + ", durationMillis=" + this.durationMillis;
    }

    public String catalogBreadthWarning(final TestProfile profile) {
        if (profile == null || profile == TestProfile.SMOKE) {
            return null;
        }
        if (this.exchangeCatalogEntries >= 25) {
            return null;
        }
        return "Harness note: exchange catalog only has "
                + this.exchangeCatalogEntries
                + " entries for profile "
                + profile.name().toLowerCase()
                + "; QA/perf contention will be concentrated unless the runtime catalog is broadened.";
    }
}
