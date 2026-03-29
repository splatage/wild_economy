package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.StoreProductsConfig;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.TransactionRunner;
import com.splatage.wild_economy.store.action.ProductActionExecutor;
import com.splatage.wild_economy.store.action.SimpleProductActionExecutor;
import com.splatage.wild_economy.store.repository.StoreEntitlementRepository;
import com.splatage.wild_economy.store.repository.StorePurchaseRepository;
import com.splatage.wild_economy.store.repository.mysql.MysqlStoreEntitlementRepository;
import com.splatage.wild_economy.store.repository.mysql.MysqlStorePurchaseRepository;
import com.splatage.wild_economy.store.repository.sqlite.SqliteStoreEntitlementRepository;
import com.splatage.wild_economy.store.repository.sqlite.SqliteStorePurchaseRepository;
import com.splatage.wild_economy.store.service.StoreService;
import com.splatage.wild_economy.store.service.StoreServiceImpl;
import com.splatage.wild_economy.store.state.StoreRuntimeStateService;
import com.splatage.wild_economy.store.state.StoreRuntimeStateServiceImpl;
import com.splatage.wild_economy.xp.service.XpBottleService;
import java.util.logging.Logger;

final class StoreBootstrap {

    private StoreBootstrap() {
    }

    static Components create(
            final DatabaseProvider databaseProvider,
            final DatabaseConfig databaseConfig,
            final TransactionRunner transactionRunner,
            final StoreProductsConfig storeProductsConfig,
            final EconomyService economyService,
            final XpBottleService xpBottleService,
            final Logger logger
    ) {
        final StoreEntitlementRepository storeEntitlementRepository = switch (databaseProvider.dialect()) {
            case SQLITE -> new SqliteStoreEntitlementRepository(databaseProvider, databaseConfig.storeTablePrefix());
            case MYSQL -> new MysqlStoreEntitlementRepository(databaseProvider, databaseConfig.storeTablePrefix());
        };

        final StorePurchaseRepository storePurchaseRepository = switch (databaseProvider.dialect()) {
            case SQLITE -> new SqliteStorePurchaseRepository(databaseConfig.storeTablePrefix());
            case MYSQL -> new MysqlStorePurchaseRepository(databaseConfig.storeTablePrefix());
        };

        final ProductActionExecutor productActionExecutor = new SimpleProductActionExecutor();

        final StoreRuntimeStateService storeRuntimeStateService = new StoreRuntimeStateServiceImpl(
                storeEntitlementRepository,
                storePurchaseRepository,
                transactionRunner,
                logger,
                databaseProvider.dialect(),
                databaseConfig.mysqlMaximumPoolSize()
        );

        final StoreService storeService = new StoreServiceImpl(
                storeProductsConfig,
                economyService,
                storeRuntimeStateService,
                productActionExecutor,
                xpBottleService
        );

        return new Components(storeRuntimeStateService, storeService, storeEntitlementRepository, storePurchaseRepository);
    }

    record Components(
            StoreRuntimeStateService storeRuntimeStateService,
            StoreService storeService,
            StoreEntitlementRepository storeEntitlementRepository,
            StorePurchaseRepository storePurchaseRepository
    ) {
    }
}
