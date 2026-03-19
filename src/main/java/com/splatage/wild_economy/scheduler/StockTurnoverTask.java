package com.splatage.wild_economy.scheduler;

import com.splatage.wild_economy.exchange.stock.StockTurnoverService;

public final class StockTurnoverTask implements Runnable {

    private final StockTurnoverService turnoverService;

    public StockTurnoverTask(final StockTurnoverService turnoverService) {
        this.turnoverService = turnoverService;
    }

    @Override
    public void run() {
        this.turnoverService.runTurnoverPass();
    }
}
package com.splatage.wild_economy.scheduler;

import com.splatage.wild_economy.exchange.stock.StockTurnoverService;

public final class StockTurnoverTask implements Runnable {

    private final StockTurnoverService turnoverService;

    public StockTurnoverTask(final StockTurnoverService turnoverService) {
        this.turnoverService = turnoverService;
    }

    @Override
    public void run() {
        this.turnoverService.runTurnoverPass();
    }
}
