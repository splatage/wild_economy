package com.splatage.wild_economy.testing.seed;

public record SeedRunReport(
        int playersSeeded,
        int economyLedgerEntriesSeeded,
        int stockRowsSeeded,
        int exchangeTransactionsSeeded,
        int supplierContributionRowsSeeded,
        int storeEntitlementsSeeded,
        int storePurchasesSeeded,
        long durationMillis
) {
    public String describe() {
        return "Seed report: players=" + this.playersSeeded
                + ", ledgerEntries=" + this.economyLedgerEntriesSeeded
                + ", stockRows=" + this.stockRowsSeeded
                + ", exchangeTransactions=" + this.exchangeTransactionsSeeded
                + ", supplierContributions=" + this.supplierContributionRowsSeeded
                + ", storeEntitlements=" + this.storeEntitlementsSeeded
                + ", storePurchases=" + this.storePurchasesSeeded
                + ", durationMillis=" + this.durationMillis;
    }
}
