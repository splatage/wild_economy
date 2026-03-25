package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.BalanceCommand;
import com.splatage.wild_economy.command.BaltopCommand;
import com.splatage.wild_economy.command.EcoCommand;
import com.splatage.wild_economy.command.PayCommand;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.command.ShopOpenSubcommand;
import com.splatage.wild_economy.command.ShopSellAllSubcommand;
import com.splatage.wild_economy.command.ShopSellContainerSubcommand;
import com.splatage.wild_economy.command.ShopSellHandSubcommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.ConfigValidator;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.economy.InternalEconomyGateway;
import com.splatage.wild_economy.economy.listener.EconomyPlayerSessionListener;
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
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.vault.WildEconomyVaultProvider;
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
import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.mysql.MysqlSchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteSchemaVersionRepository;
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
import com.splatage.wild_economy.gui.ExchangeBrowseMenu;
import com.splatage.wild_economy.gui.ExchangeItemDetailMenu;
import com.splatage.wild_economy.gui.ExchangeRootMenu;
import com.splatage.wild_economy.gui.ExchangeSubcategoryMenu;
import com.splatage.wild_economy.gui.ShopMenuListener;
import com.splatage.wild_economy.gui.ShopMenuRouter;
import com.splatage.wild_economy.gui.admin.AdminItemInspectorMenu;
import com.splatage.wild_economy.gui.admin.AdminMenuListener;
import com.splatage.wild_economy.gui.admin.AdminMenuRouter;
import com.splatage.wild_economy.gui.admin.AdminOverrideEditMenu;
import com.splatage.wild_economy.gui.admin.AdminReviewBucketMenu;
import com.splatage.wild_economy.gui.admin.AdminRootMenu;
import com.splatage.wild_economy.gui.admin.AdminRuleImpactMenu;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationDomain;
import com.splatage.wild_economy.persistence.MigrationManager;
import com.splatage.wild_economy.persistence.TransactionRunner;
import com.splatage.wild_economy.platform.PaperFoliaPlatformExecutor;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;
    private final PlatformExecutor platformExecutor;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
    private ExchangeItemsConfig exchangeItemsConfig;
    private DatabaseProvider databaseProvider;
    private ExchangeCatalog exchangeCatalog;
    private ItemNormalizer itemNormalizer;
    private ItemValidationService itemValidationService;
    private ExchangeStockRepository exchangeStockRepository;
    private ExchangeTransactionRepository exchangeTransactionRepository;
    private EconomyGateway economyGateway;
    private StockService stockService;
    private PricingService pricingService;
    private TransactionLogService transactionLogService;
    private StockTurnoverService stockTurnoverService;
    private ExchangeBrowseService exchangeBrowseService;
    private ExchangeBuyService exchangeBuyService;
    private ExchangeSellService exchangeSellService;
    private ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;
    private ShopMenuListener shopMenuListener;
    private AdminMenuRouter adminMenuRouter;
    private AdminMenuListener adminMenuListener;
    private FoliaContainerSellCoordinator foliaContainerSellCoordinator;
    private EconomyConfig economyConfig;
    private EconomyService economyService;
    private BaltopService baltopService;
    private EconomyPlayerSessionListener economyPlayerSessionListener;
    private WildEconomyVaultProvider vaultEconomyProvider;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
        this.platformExecutor = new PaperFoliaPlatformExecutor(plugin);
    }

    public void initialize() {
        final ConfigLoader configLoader = new ConfigLoader(this.plugin);
        this.globalConfig = configLoader.loadGlobalConfig();
        this.databaseConfig = configLoader.loadDatabaseConfig();
        this.exchangeItemsConfig = configLoader.loadExchangeItemsConfig();
        this.economyConfig = configLoader.loadEconomyConfig();
        this.databaseProvider = new DatabaseProvider(this.databaseConfig);

        final TransactionRunner transactionRunner = new TransactionRunner(this.databaseProvider);

        final EconomyAccountRepository economyAccountRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteEconomyAccountRepository(this.databaseProvider, this.databaseConfig.economyTablePrefix());
            case MYSQL -> new MysqlEconomyAccountRepository(this.databaseProvider, this.databaseConfig.economyTablePrefix());
        };

        final EconomyLedgerRepository economyLedgerRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteEconomyLedgerRepository(this.databaseProvider, this.databaseConfig.economyTablePrefix());
            case MYSQL -> new MysqlEconomyLedgerRepository(this.databaseProvider, this.databaseConfig.economyTablePrefix());
        };

        final EconomyNameCacheRepository economyNameCacheRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteEconomyNameCacheRepository(this.databaseProvider, this.databaseConfig.economyTablePrefix());
            case MYSQL -> new MysqlEconomyNameCacheRepository(this.databaseProvider, this.databaseConfig.economyTablePrefix());
        };

        final BalanceCache balanceCache = new BalanceCache();
        this.baltopService = new BaltopServiceImpl(
                economyAccountRepository,
                economyNameCacheRepository,
                this.economyConfig
        );
        this.economyService = new EconomyServiceImpl(
                this.economyConfig,
                economyAccountRepository,
                economyLedgerRepository,
                economyNameCacheRepository,
                transactionRunner,
                balanceCache,
                this.baltopService
        );

        final SchemaVersionRepository schemaVersionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(this.databaseProvider);
            case MYSQL -> new MysqlSchemaVersionRepository(this.databaseProvider);
        };

        final MigrationManager exchangeMigrationManager = new MigrationManager(
                this.databaseProvider,
                this.databaseConfig,
                schemaVersionRepository,
                MigrationDomain.EXCHANGE
        );
        exchangeMigrationManager.migrate();

        final MigrationManager economyMigrationManager = new MigrationManager(
                this.databaseProvider,
                this.databaseConfig,
                schemaVersionRepository,
                MigrationDomain.ECONOMY
        );
        economyMigrationManager.migrate();

        this.exchangeStockRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(this.databaseProvider, this.databaseConfig.exchangeTablePrefix());
            case MYSQL -> new MysqlExchangeStockRepository(this.databaseProvider, this.databaseConfig.exchangeTablePrefix());
        };

        this.exchangeTransactionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(this.databaseProvider, this.databaseConfig.exchangeTablePrefix());
            case MYSQL -> new MysqlExchangeTransactionRepository(this.databaseProvider, this.databaseConfig.exchangeTablePrefix());
        }
;
        final CatalogLoader catalogLoader = new CatalogLoader();
        this.exchangeCatalog = Objects.requireNonNull(
                catalogLoader.load(this.exchangeItemsConfig),
                "exchangeCatalog"
        );

        final ConfigValidator configValidator = new ConfigValidator(
                this.exchangeItemsConfig,
                this.exchangeCatalog
        );
        configValidator.validate();

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        this.itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        this.itemValidationService = new ItemValidationServiceImpl(this.itemNormalizer, this.exchangeCatalog);
        this.economyGateway = new InternalEconomyGateway(this.economyService, this.economyConfig);

        final StockStateResolver stockStateResolver = new StockStateResolver();
        this.stockService = new StockServiceImpl(
                this.exchangeStockRepository,
                this.exchangeCatalog,
                stockStateResolver,
                this.plugin.getLogger(),
                this.databaseProvider.dialect(),
                this.databaseConfig.mysqlMaximumPoolSize()
        );
        this.stockService.flushDirtyNow();
        this.pricingService = new PricingServiceImpl(this.exchangeCatalog);

        this.transactionLogService = new TransactionLogServiceImpl(
                this.exchangeTransactionRepository,
                this.plugin.getLogger(),
                this.databaseProvider.dialect(),
                this.databaseConfig.mysqlMaximumPoolSize()
        );

        this.stockTurnoverService = new StockTurnoverServiceImpl(this.exchangeCatalog, this.stockService, this.transactionLogService);
        this.exchangeBrowseService = new ExchangeBrowseServiceImpl(this.exchangeCatalog, this.stockService);

        final ExchangeBuyService rawBuyService = new ExchangeBuyServiceImpl(
                this.exchangeCatalog,
                this.itemValidationService,
                this.stockService,
                this.pricingService,
                this.economyGateway,
                this.transactionLogService,
                this.globalConfig
        );

        final ExchangeSellServiceImpl rawSellService = new ExchangeSellServiceImpl(
                this.exchangeCatalog,
                this.itemValidationService,
                this.stockService,
                this.pricingService,
                this.economyGateway,
                this.transactionLogService
        );

        this.exchangeBuyService = new FoliaSafeExchangeBuyService(rawBuyService);
        this.exchangeSellService = new FoliaSafeExchangeSellService(rawSellService);
        this.exchangeService = new ExchangeServiceImpl(this.exchangeBrowseService, this.exchangeBuyService, this.exchangeSellService);
        this.foliaContainerSellCoordinator = new FoliaContainerSellCoordinator(this.platformExecutor, this.exchangeService, rawSellService);

        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(this.exchangeService);
        final ExchangeSubcategoryMenu subcategoryMenu = new ExchangeSubcategoryMenu(this.exchangeService);
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService);
        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService, this.platformExecutor);
        this.shopMenuRouter = new ShopMenuRouter(this.platformExecutor, rootMenu, subcategoryMenu, browseMenu, itemDetailMenu);
        rootMenu.setShopMenuRouter(this.shopMenuRouter);
        subcategoryMenu.setShopMenuRouter(this.shopMenuRouter);
        browseMenu.setShopMenuRouter(this.shopMenuRouter);
        itemDetailMenu.setShopMenuRouter(this.shopMenuRouter);
        this.shopMenuListener = new ShopMenuListener(rootMenu, subcategoryMenu, browseMenu, itemDetailMenu);
        this.plugin.getServer().getPluginManager().registerEvents(this.shopMenuListener, this.plugin);

        final AdminRootMenu adminRootMenu = new AdminRootMenu();
        final AdminReviewBucketMenu adminReviewBucketMenu = new AdminReviewBucketMenu();
        final AdminRuleImpactMenu adminRuleImpactMenu = new AdminRuleImpactMenu();
        final AdminItemInspectorMenu adminItemInspectorMenu = new AdminItemInspectorMenu();
        final AdminOverrideEditMenu adminOverrideEditMenu = new AdminOverrideEditMenu();
        this.adminMenuRouter = new AdminMenuRouter(
                this.plugin,
                this.platformExecutor,
                adminRootMenu,
                adminReviewBucketMenu,
                adminRuleImpactMenu,
                adminItemInspectorMenu,
                adminOverrideEditMenu
        );
        adminRootMenu.setAdminMenuRouter(this.adminMenuRouter);
        adminReviewBucketMenu.setAdminMenuRouter(this.adminMenuRouter);
        adminRuleImpactMenu.setAdminMenuRouter(this.adminMenuRouter);
        adminItemInspectorMenu.setAdminMenuRouter(this.adminMenuRouter);
        adminOverrideEditMenu.setAdminMenuRouter(this.adminMenuRouter);
        this.adminMenuListener = new AdminMenuListener(
                adminRootMenu,
                adminReviewBucketMenu,
                adminRuleImpactMenu,
                adminItemInspectorMenu,
                adminOverrideEditMenu
        );
        this.economyPlayerSessionListener = new EconomyPlayerSessionListener(this.economyService);
        this.plugin.getServer().getPluginManager().registerEvents(this.economyPlayerSessionListener, this.plugin);

        for (final Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
           this.economyService.warmPlayerSession(onlinePlayer.getUniqueId(), onlinePlayer.getName());
        }

        if (this.plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            this.vaultEconomyProvider = new WildEconomyVaultProvider(this.plugin, this.economyService, this.economyConfig);
            this.plugin.getServer().getServicesManager().register(
                    Economy.class,
                    this.vaultEconomyProvider,
                    this.plugin,
                    ServicePriority.Highest
            );
        }

        this.plugin.getServer().getPluginManager().registerEvents(this.adminMenuListener, this.plugin);
    }

    public void registerCommands() {
        final ShopOpenSubcommand openSubcommand = new ShopOpenSubcommand(this.shopMenuRouter);
        final ShopSellHandSubcommand sellHandSubcommand = new ShopSellHandSubcommand(this.exchangeService, this.platformExecutor);
        final ShopSellAllSubcommand sellAllSubcommand = new ShopSellAllSubcommand(this.exchangeService, this.platformExecutor);
        final ShopSellContainerSubcommand sellContainerSubcommand = new ShopSellContainerSubcommand(this.foliaContainerSellCoordinator);

        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(openSubcommand, sellHandSubcommand, sellAllSubcommand, sellContainerSubcommand));
        }

        final PluginCommand sellHand = this.plugin.getCommand("sellhand");
        if (sellHand != null) {
            sellHand.setExecutor(sellHandSubcommand);
        }

        final PluginCommand sellAll = this.plugin.getCommand("sellall");
        if (sellAll != null) {
            sellAll.setExecutor(sellAllSubcommand);
        }

        final PluginCommand sellContainer = this.plugin.getCommand("sellcontainer");
        if (sellContainer != null) {
            sellContainer.setExecutor(sellContainerSubcommand);
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand(this.plugin, this.adminMenuRouter));
        }

        final BalanceCommand balanceCommand = new BalanceCommand(this.economyService, this.economyConfig);
        final PayCommand payCommand = new PayCommand(this.economyService, this.economyConfig);
        final BaltopCommand baltopCommand = new BaltopCommand(this.baltopService, this.economyConfig);
        final EcoCommand ecoCommand = new EcoCommand(this.economyService, this.economyConfig);

        final PluginCommand balance = this.plugin.getCommand("balance");
        if (balance != null) {
            balance.setExecutor(balanceCommand);
        }

        final PluginCommand pay = this.plugin.getCommand("pay");
        if (pay != null) {
            pay.setExecutor(payCommand);
        }

        final PluginCommand baltop = this.plugin.getCommand("baltop");
        if (baltop != null) {
            baltop.setExecutor(baltopCommand);
        }

        final PluginCommand eco = this.plugin.getCommand("eco");
        if (eco != null) {
            eco.setExecutor(ecoCommand);
        }
    }

    public void registerTasks() {
        this.platformExecutor.runGlobalRepeating(
                new com.splatage.wild_economy.scheduler.StockTurnoverTask(this.stockTurnoverService),
                this.globalConfig.turnoverIntervalTicks(),
                this.globalConfig.turnoverIntervalTicks()
        );
    }

    public void shutdown() {
         if (this.economyPlayerSessionListener != null) {
             HandlerList.unregisterAll(this.economyPlayerSessionListener);
            this.economyPlayerSessionListener = null;
        }
        if (this.shopMenuListener != null) {
            HandlerList.unregisterAll(this.shopMenuListener);
            this.shopMenuListener = null;
        }
        if (this.adminMenuListener != null) {
            HandlerList.unregisterAll(this.adminMenuListener);
            this.adminMenuListener = null;
        }
        if (this.shopMenuRouter != null) {
            this.shopMenuRouter.closeAllShopViews();
            this.shopMenuRouter = null;
        }
        if (this.adminMenuRouter != null) {
            this.adminMenuRouter.closeAllAdminViews();
            this.adminMenuRouter = null;
        }
        if (this.vaultEconomyProvider != null) {
            this.plugin.getServer().getServicesManager().unregister(Economy.class, this.vaultEconomyProvider);
            this.vaultEconomyProvider = null;
        }

        this.platformExecutor.cancelPluginTasks();

        if (this.transactionLogService != null) {
            this.transactionLogService.shutdown();
            this.transactionLogService = null;
        }
        if (this.stockService != null) {
            this.stockService.shutdown();
            this.stockService = null;
        }
        if (this.economyService != null) {
            for (final Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
                this.economyService.flushPlayerSession(onlinePlayer.getUniqueId(), onlinePlayer.getName());
            }
            this.economyService = null;
        }
        this.baltopService = null;
        this.economyConfig = null;
        if (this.databaseProvider != null) {
            this.databaseProvider.close();
            this.databaseProvider = null;
        }

        this.exchangeService = null;
        this.exchangeBuyService = null;
        this.exchangeSellService = null;
        this.exchangeBrowseService = null;
        this.stockTurnoverService = null;
        this.foliaContainerSellCoordinator = null;
        this.economyGateway = null;
    }
}
