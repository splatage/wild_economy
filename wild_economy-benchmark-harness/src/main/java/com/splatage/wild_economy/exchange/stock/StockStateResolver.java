package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.domain.StockState;

public final class StockStateResolver {

    public StockState resolve(final long stockCount, final long stockCap) {
        if (stockCap <= 0L) {
            return stockCount <= 0L ? StockState.OUT_OF_STOCK : StockState.HEALTHY;
        }
        if (stockCount <= 0L) {
            return StockState.OUT_OF_STOCK;
        }

        final double fillRatio = (double) stockCount / (double) stockCap;
        if (fillRatio >= 1.0D) {
            return StockState.SATURATED;
        }
        if (fillRatio >= 0.75D) {
            return StockState.HIGH;
        }
        if (fillRatio >= 0.25D) {
            return StockState.HEALTHY;
        }
        return StockState.LOW;
    }
}
