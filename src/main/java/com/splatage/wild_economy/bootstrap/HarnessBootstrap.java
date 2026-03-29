package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.config.StoreProductsConfig;
import com.splatage.wild_economy.economy.repository.EconomyAccountRepository;
import com.splatage.wild_economy.economy.repository.EconomyLedgerRepository;
import com.splatage.wild_economy.economy.repository.EconomyNameCacheRepository;
import com.splatage.wild_economy.economy.service.BaltopService;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.exchange.activity.MarketActivityService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.SupplierStatsRepository;
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.exchange.supplier.SupplierStatsService;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.TransactionRunner;
import com.splatage.wild_economy.platform.InlinePlatformExecutor;
import com.splatage.wild_economy.store.repository.StoreEntitlementRepository;
import com.splatage.wild_economy.store.repository.StorePurchaseRepository;
import com.splatage.wild_economy.store.service.StoreService;
import com.splatage.wild_economy.store.state.StoreRuntimeStateService;
import com.splatage.wild_economy.xp.service.XpBottleService;
import com.splatage.wild_economy.xp.service.NoopXpBottleService;
import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

public final class HarnessBootstrap {

    private HarnessBootstrap() {
    }

    public static HarnessComponents create(final File configDirectory, final Logger logger) {
        Objects.requireNonNull(configDirectory, "configDirectory");
        Objects.requireNonNull(logger, "logger");

        final ConfigLoader configLoader = new ConfigLoader(configDirectory);
        final GlobalConfig globalConfig = configLoader.loadGlobalConfig();
        final DatabaseConfig databaseConfig = configLoader.loadDatabaseConfig();
        final ExchangeItemsConfig exchangeItemsConfig = configLoader.loadExchangeItemsConfig();
        final EconomyConfig economyConfig = configLoader.loadEconomyConfig();
        final StoreProductsConfig storeProductsConfig = configLoader.loadStoreProductsConfig(economyConfig);

        final DatabaseProvider databaseProvider = new DatabaseProvider(databaseConfig);
        final TransactionRunner transactionRunner = new TransactionRunner(databaseProvider);
        final SchemaVersionRepository schemaVersionRepository = MigrationBootstrap.createSchemaVersionRepository(databaseProvider);
        MigrationBootstrap.migrateAll(databaseProvider, databaseConfig, schemaVersionRepository);

        final EconomyBootstrap.Components economyComponents = EconomyBootstrap.create(
                databaseProvider,
                databaseConfig,
                economyConfig,
                transactionRunner
        );

        final XpBottleService xpBottleService = new NoopXpBottleService();
        final StoreBootstrap.Components storeComponents = StoreBootstrap.create(
                databaseProvider,
                databaseConfig,
                transactionRunner,
                storeProductsConfig,
                economyComponents.economyService(),
                xpBottleService,
                logger
        );

        final InlinePlatformExecutor platformExecutor = new InlinePlatformExecutor();
        final ExchangeBootstrap.Components exchangeComponents = ExchangeBootstrap.create(
                configDirectory,
                logger,
                databaseProvider,
                databaseConfig,
                exchangeItemsConfig,
                globalConfig,
                economyComponents.economyService(),
                economyConfig,
                economyComponents.economyNameCacheRepository(),
                platformExecutor
        );

        return new HarnessComponents(
                configDirectory,
                globalConfig,
                databaseConfig,
                economyConfig,
                exchangeItemsConfig,
                storeProductsConfig,
                databaseProvider,
                transactionRunner,
                economyComponents.economyService(),
                economyComponents.baltopService(),
                economyComponents.economyNameCacheRepository(),
                economyComponents.economyAccountRepository(),
                economyComponents.economyLedgerRepository(),
                exchangeComponents.exchangeCatalog(),
                exchangeComponents.exchangeStockRepository(),
                exchangeComponents.exchangeTransactionRepository(),
                exchangeComponents.supplierStatsRepository(),
                exchangeComponents.stockService(),
                exchangeComponents.pricingService(),
                exchangeComponents.transactionLogService(),
                exchangeComponents.marketActivityService(),
                exchangeComponents.supplierStatsService(),
                storeComponents.storeRuntimeStateService(),
                storeComponents.storeService(),
                storeComponents.storeEntitlementRepository(),
                storeComponents.storePurchaseRepository(),
                platformExecutor,
                xpBottleService
        );
    }

    public record HarnessComponents(
            File configDirectory,
            GlobalConfig globalConfig,
            DatabaseConfig databaseConfig,
            EconomyConfig economyConfig,
            ExchangeItemsConfig exchangeItemsConfig,
            StoreProductsConfig storeProductsConfig,
            DatabaseProvider databaseProvider,
            TransactionRunner transactionRunner,
            EconomyService economyService,
            BaltopService baltopService,
            EconomyNameCacheRepository economyNameCacheRepository,
            EconomyAccountRepository economyAccountRepository,
            EconomyLedgerRepository economyLedgerRepository,
            ExchangeCatalog exchangeCatalog,
            ExchangeStockRepository exchangeStockRepository,
            ExchangeTransactionRepository exchangeTransactionRepository,
            SupplierStatsRepository supplierStatsRepository,
            StockService stockService,
            PricingService pricingService,
            TransactionLogService transactionLogService,
            MarketActivityService marketActivityService,
            SupplierStatsService supplierStatsService,
            StoreRuntimeStateService storeRuntimeStateService,
            StoreService storeService,
            StoreEntitlementRepository storeEntitlementRepository,
            StorePurchaseRepository storePurchaseRepository,
            InlinePlatformExecutor platformExecutor,
            XpBottleService xpBottleService
    ) implements AutoCloseable {
        @Override
        public void close() {
            this.storeRuntimeStateService.shutdown();
            this.stockService.shutdown();
            this.transactionLogService.shutdown();
            this.supplierStatsService.shutdown();
            this.platformExecutor.close();
            this.databaseProvider.close();
        }
    }
}
