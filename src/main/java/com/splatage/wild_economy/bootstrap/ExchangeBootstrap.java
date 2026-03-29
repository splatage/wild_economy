package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.InternalEconomyGateway;
import com.splatage.wild_economy.economy.repository.EconomyNameCacheRepository;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.exchange.activity.MarketActivityService;
import com.splatage.wild_economy.exchange.activity.MarketActivityServiceImpl;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.item.BukkitItemNormalizer;
import com.splatage.wild_economy.exchange.item.CanonicalItemRules;
import com.splatage.wild_economy.exchange.item.ItemNormalizer;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.item.ItemValidationServiceImpl;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.pricing.PricingServiceImpl;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.SupplierStatsRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlSupplierStatsRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteSupplierStatsRepository;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseService;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeBuyService;
import com.splatage.wild_economy.exchange.service.ExchangeBuyServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeSellService;
import com.splatage.wild_economy.exchange.service.ExchangeSellServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.exchange.service.ExchangeServiceImpl;
import com.splatage.wild_economy.exchange.service.FoliaContainerSellCoordinator;
import com.splatage.wild_economy.exchange.service.FoliaSafeExchangeBuyService;
import com.splatage.wild_economy.exchange.service.FoliaSafeExchangeSellService;
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import com.splatage.wild_economy.exchange.service.TransactionLogServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.exchange.stock.StockServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockStateResolver;
import com.splatage.wild_economy.exchange.stock.StockTurnoverService;
import com.splatage.wild_economy.exchange.stock.StockTurnoverServiceImpl;
import com.splatage.wild_economy.exchange.supplier.SupplierStatsService;
import com.splatage.wild_economy.exchange.supplier.SupplierStatsServiceImpl;
import com.splatage.wild_economy.gui.browse.ExchangeLayoutBrowseService;
import com.splatage.wild_economy.gui.browse.ExchangeLayoutBrowseServiceImpl;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import com.splatage.wild_economy.gui.layout.LayoutBlueprintLoader;
import com.splatage.wild_economy.gui.layout.LayoutPlacementResolver;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.io.IOException;
import java.util.Objects;

final class ExchangeBootstrap {

    private ExchangeBootstrap() {
    }

    static Components create(
            final WildEconomyPlugin plugin,
            final DatabaseProvider databaseProvider,
            final DatabaseConfig databaseConfig,
            final ExchangeItemsConfig exchangeItemsConfig,
            final GlobalConfig globalConfig,
            final EconomyService economyService,
            final EconomyConfig economyConfig,
            final EconomyNameCacheRepository economyNameCacheRepository,
            final PlatformExecutor platformExecutor
    ) {
        final ExchangeStockRepository exchangeStockRepository = switch (databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(databaseProvider, databaseConfig.exchangeTablePrefix());
            case MYSQL -> new MysqlExchangeStockRepository(databaseProvider, databaseConfig.exchangeTablePrefix());
        };

        final ExchangeTransactionRepository exchangeTransactionRepository = switch (databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(databaseProvider, databaseConfig.exchangeTablePrefix());
            case MYSQL -> new MysqlExchangeTransactionRepository(databaseProvider, databaseConfig.exchangeTablePrefix());
        };

        final SupplierStatsRepository supplierStatsRepository = switch (databaseProvider.dialect()) {
            case SQLITE -> new SqliteSupplierStatsRepository(databaseProvider, databaseConfig.exchangeTablePrefix());
            case MYSQL -> new MysqlSupplierStatsRepository(databaseProvider, databaseConfig.exchangeTablePrefix());
        };

        final LayoutBlueprint layoutBlueprint;
        try {
            layoutBlueprint = new LayoutBlueprintLoader().load(
                    plugin.getDataFolder().toPath().resolve("layout.yml").toFile()
            );
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to load layout.yml", exception);
        }

        final CatalogLoader catalogLoader = new CatalogLoader();
        final ExchangeCatalog exchangeCatalog = Objects.requireNonNull(
                catalogLoader.load(exchangeItemsConfig),
                "exchangeCatalog"
        );

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        final ItemNormalizer itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        final ItemValidationService itemValidationService = new ItemValidationServiceImpl(itemNormalizer, exchangeCatalog);
        final EconomyGateway economyGateway = new InternalEconomyGateway(economyService, economyConfig);

        final StockStateResolver stockStateResolver = new StockStateResolver();
        final StockService stockService = new StockServiceImpl(
                exchangeStockRepository,
                exchangeCatalog,
                stockStateResolver,
                plugin.getLogger(),
                databaseProvider.dialect(),
                databaseConfig.mysqlMaximumPoolSize()
        );
        stockService.flushDirtyNow();

        final PricingService pricingService = new PricingServiceImpl(exchangeCatalog);
        final TransactionLogService transactionLogService = new TransactionLogServiceImpl(
                exchangeTransactionRepository,
                plugin.getLogger(),
                databaseProvider.dialect(),
                databaseConfig.mysqlMaximumPoolSize()
        );
        final SupplierStatsService supplierStatsService = new SupplierStatsServiceImpl(
                supplierStatsRepository,
                economyNameCacheRepository,
                exchangeCatalog,
                plugin.getLogger(),
                databaseProvider.dialect(),
                databaseConfig.mysqlMaximumPoolSize()
        );
        final MarketActivityService marketActivityService = new MarketActivityServiceImpl(
                exchangeTransactionRepository,
                supplierStatsRepository,
                exchangeCatalog,
                globalConfig.recentWindowHours()
        );
        final StockTurnoverService stockTurnoverService = new StockTurnoverServiceImpl(
                exchangeCatalog,
                stockService,
                transactionLogService
        );
        final ExchangeBrowseService exchangeBrowseService = new ExchangeBrowseServiceImpl(
                exchangeCatalog,
                stockService,
                pricingService
        );
        final ExchangeLayoutBrowseService exchangeLayoutBrowseService = new ExchangeLayoutBrowseServiceImpl(
                exchangeCatalog,
                exchangeBrowseService,
                stockService,
                layoutBlueprint,
                new LayoutPlacementResolver(layoutBlueprint)
        );

        final ExchangeBuyService rawBuyService = new ExchangeBuyServiceImpl(
                exchangeCatalog,
                itemValidationService,
                stockService,
                pricingService,
                economyGateway,
                transactionLogService,
                globalConfig
        );

        final ExchangeSellServiceImpl rawSellService = new ExchangeSellServiceImpl(
                exchangeCatalog,
                itemValidationService,
                stockService,
                pricingService,
                economyGateway,
                transactionLogService,
                supplierStatsService
        );

        final ExchangeBuyService exchangeBuyService = new FoliaSafeExchangeBuyService(rawBuyService);
        final ExchangeSellService exchangeSellService = new FoliaSafeExchangeSellService(rawSellService);
        final ExchangeService exchangeService = new ExchangeServiceImpl(
                exchangeBrowseService,
                exchangeBuyService,
                exchangeSellService
        );
        final FoliaContainerSellCoordinator foliaContainerSellCoordinator = new FoliaContainerSellCoordinator(
                platformExecutor,
                exchangeService,
                rawSellService
        );

        return new Components(
                exchangeCatalog,
                layoutBlueprint,
                itemNormalizer,
                itemValidationService,
                exchangeStockRepository,
                exchangeTransactionRepository,
                economyGateway,
                stockService,
                pricingService,
                transactionLogService,
                stockTurnoverService,
                marketActivityService,
                exchangeBrowseService,
                exchangeLayoutBrowseService,
                exchangeBuyService,
                exchangeSellService,
                exchangeService,
                foliaContainerSellCoordinator,
                supplierStatsService
        );
    }

    record Components(
            ExchangeCatalog exchangeCatalog,
            LayoutBlueprint layoutBlueprint,
            ItemNormalizer itemNormalizer,
            ItemValidationService itemValidationService,
            ExchangeStockRepository exchangeStockRepository,
            ExchangeTransactionRepository exchangeTransactionRepository,
            EconomyGateway economyGateway,
            StockService stockService,
            PricingService pricingService,
            TransactionLogService transactionLogService,
            StockTurnoverService stockTurnoverService,
            MarketActivityService marketActivityService,
            ExchangeBrowseService exchangeBrowseService,
            ExchangeLayoutBrowseService exchangeLayoutBrowseService,
            ExchangeBuyService exchangeBuyService,
            ExchangeSellService exchangeSellService,
            ExchangeService exchangeService,
            FoliaContainerSellCoordinator foliaContainerSellCoordinator,
            SupplierStatsService supplierStatsService
    ) {
    }
}
