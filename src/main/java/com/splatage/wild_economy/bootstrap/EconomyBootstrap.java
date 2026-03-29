package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.repository.EconomyAccountRepository;
import com.splatage.wild_economy.economy.repository.EconomyLedgerRepository;
import com.splatage.wild_economy.economy.repository.EconomyNameCacheRepository;
import com.splatage.wild_economy.economy.repository.mysql.MysqlEconomyAccountRepository;
import com.splatage.wild_economy.economy.repository.mysql.MysqlEconomyLedgerRepository;
import com.splatage.wild_economy.economy.repository.mysql.MysqlEconomyNameCacheRepository;
import com.splatage.wild_economy.economy.repository.sqlite.SqliteEconomyAccountRepository;
import com.splatage.wild_economy.economy.repository.sqlite.SqliteEconomyLedgerRepository;
import com.splatage.wild_economy.economy.repository.sqlite.SqliteEconomyNameCacheRepository;
import com.splatage.wild_economy.economy.service.BalanceCache;
import com.splatage.wild_economy.economy.service.BaltopService;
import com.splatage.wild_economy.economy.service.BaltopServiceImpl;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.economy.service.EconomyServiceImpl;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.TransactionRunner;

final class EconomyBootstrap {

    private EconomyBootstrap() {
    }

    static Components create(
            final DatabaseProvider databaseProvider,
            final DatabaseConfig databaseConfig,
            final EconomyConfig economyConfig,
            final TransactionRunner transactionRunner
    ) {
        final EconomyAccountRepository economyAccountRepository = switch (databaseProvider.dialect()) {
            case SQLITE -> new SqliteEconomyAccountRepository(databaseProvider, databaseConfig.economyTablePrefix());
            case MYSQL -> new MysqlEconomyAccountRepository(databaseProvider, databaseConfig.economyTablePrefix());
        };

        final EconomyLedgerRepository economyLedgerRepository = switch (databaseProvider.dialect()) {
            case SQLITE -> new SqliteEconomyLedgerRepository(databaseProvider, databaseConfig.economyTablePrefix());
            case MYSQL -> new MysqlEconomyLedgerRepository(databaseProvider, databaseConfig.economyTablePrefix());
        };

        final EconomyNameCacheRepository economyNameCacheRepository = switch (databaseProvider.dialect()) {
            case SQLITE -> new SqliteEconomyNameCacheRepository(databaseProvider, databaseConfig.economyTablePrefix());
            case MYSQL -> new MysqlEconomyNameCacheRepository(databaseProvider, databaseConfig.economyTablePrefix());
        };

        final BalanceCache balanceCache = new BalanceCache();
        final BaltopService baltopService = new BaltopServiceImpl(
                economyAccountRepository,
                economyNameCacheRepository,
                economyConfig
        );
        final EconomyService economyService = new EconomyServiceImpl(
                economyConfig,
                economyAccountRepository,
                economyLedgerRepository,
                economyNameCacheRepository,
                transactionRunner,
                balanceCache,
                baltopService
        );

        return new Components(economyService, baltopService, economyNameCacheRepository);
    }

    record Components(
            EconomyService economyService,
            BaltopService baltopService,
            EconomyNameCacheRepository economyNameCacheRepository
    ) {
    }
}
