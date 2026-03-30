package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.BalanceCommand;
import com.splatage.wild_economy.command.BaltopCommand;
import com.splatage.wild_economy.command.EcoCommand;
import com.splatage.wild_economy.command.PayCommand;
import com.splatage.wild_economy.command.SellCommand;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.command.ShopOpenSubcommand;
import com.splatage.wild_economy.command.ShopSellAllSubcommand;
import com.splatage.wild_economy.command.ShopSellContainerSubcommand;
import com.splatage.wild_economy.command.ShopSellHandSubcommand;
import com.splatage.wild_economy.command.ShopSellPreviewSubcommand;
import com.splatage.wild_economy.command.ShopTopSubcommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.ConfigValidator;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.config.StoreProductsConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.listener.EconomyPlayerSessionListener;
import com.splatage.wild_economy.economy.placeholder.WildEconomyExpansion;
import com.splatage.wild_economy.economy.service.BaltopService;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.economy.vault.WildEconomyVaultProvider;
import com.splatage.wild_economy.exchange.activity.MarketActivityService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.item.ItemNormalizer;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseService;
import com.splatage.wild_economy.exchange.service.ExchangeBuyService;
import com.splatage.wild_economy.exchange.service.ExchangeSellService;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.exchange.service.FoliaContainerSellCoordinator;
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.exchange.stock.StockTurnoverService;
import com.splatage.wild_economy.exchange.supplier.SupplierStatsService;
import com.splatage.wild_economy.gui.PlayerHeadCache;
import com.splatage.wild_economy.gui.PlayerInfoItemFactory;
import com.splatage.wild_economy.gui.ShopMenuListener;
import com.splatage.wild_economy.gui.ShopMenuRouter;
import com.splatage.wild_economy.gui.admin.AdminMenuListener;
import com.splatage.wild_economy.gui.admin.AdminMenuRouter;
import com.splatage.wild_economy.gui.browse.ExchangeLayoutBrowseService;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.TransactionRunner;
import com.splatage.wild_economy.platform.PaperFoliaPlatformExecutor;
import com.splatage.wild_economy.platform.PlatformExecutor;
import com.splatage.wild_economy.scheduler.StockTurnoverTask;
import com.splatage.wild_economy.store.listener.StorePlayerSessionListener;
import com.splatage.wild_economy.store.listener.StoreProgressListener;
import com.splatage.wild_economy.store.progress.StoreProgressService;
import com.splatage.wild_economy.store.service.StoreService;
import com.splatage.wild_economy.store.state.StoreRuntimeStateService;
import com.splatage.wild_economy.xp.listener.XpBottleRedeemListener;
import com.splatage.wild_economy.xp.service.XpBottleService;
import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;
    private final PlatformExecutor platformExecutor;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
    private ExchangeItemsConfig exchangeItemsConfig;
    private DatabaseProvider databaseProvider;
    private ExchangeCatalog exchangeCatalog;
    private LayoutBlueprint layoutBlueprint;
    private ItemNormalizer itemNormalizer;
    private ItemValidationService itemValidationService;
    private ExchangeStockRepository exchangeStockRepository;
    private ExchangeTransactionRepository exchangeTransactionRepository;
    private EconomyGateway economyGateway;
    private StockService stockService;
    private PricingService pricingService;
    private TransactionLogService transactionLogService;
    private StockTurnoverService stockTurnoverService;
    private MarketActivityService marketActivityService;
    private ExchangeBrowseService exchangeBrowseService;
    private ExchangeLayoutBrowseService exchangeLayoutBrowseService;
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
    private StorePlayerSessionListener storePlayerSessionListener;
    private StoreProgressListener storeProgressListener;
    private StoreProductsConfig storeProductsConfig;
    private StoreRuntimeStateService storeRuntimeStateService;
    private StoreProgressService storeProgressService;
    private StoreService storeService;
    private WildEconomyVaultProvider vaultEconomyProvider;
    private WildEconomyExpansion placeholderExpansion;
    private XpBottleService xpBottleService;
    private SupplierStatsService supplierStatsService;
    private XpBottleRedeemListener xpBottleRedeemListener;

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
        this.storeProductsConfig = configLoader.loadStoreProductsConfig(this.economyConfig);
        this.databaseProvider = new DatabaseProvider(this.databaseConfig);
        final TransactionRunner transactionRunner = new TransactionRunner(this.databaseProvider);

        final EconomyBootstrap.Components economyComponents = EconomyBootstrap.create(
                this.databaseProvider,
                this.databaseConfig,
                this.economyConfig,
                transactionRunner
        );
        this.economyService = economyComponents.economyService();
        this.baltopService = economyComponents.baltopService();

        this.xpBottleService = new com.splatage.wild_economy.xp.service.XpBottleServiceImpl(this.plugin);

        final PlayerHeadCache playerHeadCache = new PlayerHeadCache(this.plugin);
        final PlayerInfoItemFactory playerInfoItemFactory = new PlayerInfoItemFactory(
                playerHeadCache,
                this.economyService,
                this.xpBottleService,
                this.economyConfig
        );

        final StoreBootstrap.Components storeComponents = StoreBootstrap.create(
                this.plugin,
                this.databaseProvider,
                this.databaseConfig,
                transactionRunner,
                this.storeProductsConfig,
                this.globalConfig,
                this.economyService,
                this.xpBottleService,
                this.plugin.getLogger()
        );
        this.storeRuntimeStateService = storeComponents.storeRuntimeStateService();
        this.storeProgressService = storeComponents.storeProgressService();
        this.storeService = storeComponents.storeService();

        final SchemaVersionRepository schemaVersionRepository = MigrationBootstrap.createSchemaVersionRepository(this.databaseProvider);
        MigrationBootstrap.migrateAll(this.databaseProvider, this.databaseConfig, schemaVersionRepository);

        final ExchangeBootstrap.Components exchangeComponents = ExchangeBootstrap.create(
                this.plugin.getDataFolder(),
                this.plugin.getLogger(),
                this.databaseProvider,
                this.databaseConfig,
                this.exchangeItemsConfig,
                this.globalConfig,
                this.economyService,
                this.economyConfig,
                economyComponents.economyNameCacheRepository(),
                this.platformExecutor
        );
        this.exchangeCatalog = exchangeComponents.exchangeCatalog();
        this.layoutBlueprint = exchangeComponents.layoutBlueprint();
        this.itemNormalizer = exchangeComponents.itemNormalizer();
        this.itemValidationService = exchangeComponents.itemValidationService();
        this.exchangeStockRepository = exchangeComponents.exchangeStockRepository();
        this.exchangeTransactionRepository = exchangeComponents.exchangeTransactionRepository();
        this.economyGateway = exchangeComponents.economyGateway();
        this.stockService = exchangeComponents.stockService();
        this.pricingService = exchangeComponents.pricingService();
        this.transactionLogService = exchangeComponents.transactionLogService();
        this.stockTurnoverService = exchangeComponents.stockTurnoverService();
        this.marketActivityService = exchangeComponents.marketActivityService();
        this.exchangeBrowseService = exchangeComponents.exchangeBrowseService();
        this.exchangeLayoutBrowseService = exchangeComponents.exchangeLayoutBrowseService();
        this.exchangeBuyService = exchangeComponents.exchangeBuyService();
        this.exchangeSellService = exchangeComponents.exchangeSellService();
        this.exchangeService = exchangeComponents.exchangeService();
        this.foliaContainerSellCoordinator = exchangeComponents.foliaContainerSellCoordinator();
        this.supplierStatsService = exchangeComponents.supplierStatsService();

        final ConfigValidator configValidator = new ConfigValidator(
                this.exchangeItemsConfig,
                this.exchangeCatalog
        );
        configValidator.validate();

        final GuiBootstrap.Components guiComponents = GuiBootstrap.create(
                this.plugin,
                this.platformExecutor,
                this.exchangeLayoutBrowseService,
                this.exchangeService,
                this.supplierStatsService,
                this.marketActivityService,
                playerInfoItemFactory,
                this.layoutBlueprint,
                this.storeService,
                this.economyConfig,
                this.xpBottleService,
                this.economyService
        );
        this.shopMenuRouter = guiComponents.shopMenuRouter();
        this.shopMenuListener = guiComponents.shopMenuListener();
        this.adminMenuRouter = guiComponents.adminMenuRouter();
        this.adminMenuListener = guiComponents.adminMenuListener();
        this.xpBottleRedeemListener = guiComponents.xpBottleRedeemListener();

        this.economyPlayerSessionListener = new EconomyPlayerSessionListener(this.plugin, this.economyService);
        this.storePlayerSessionListener = new StorePlayerSessionListener(this.storeRuntimeStateService);
        this.storeProgressListener = new StoreProgressListener(this.storeProgressService);

        this.registerEventListeners();
        this.warmOnlineEconomySessions();
        this.registerVaultIfPresent();
        this.registerPlaceholderApiIfPresent();
    }

    private void registerEventListeners() {
        this.plugin.getServer().getPluginManager().registerEvents(this.shopMenuListener, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.adminMenuListener, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.economyPlayerSessionListener, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.storePlayerSessionListener, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.storeProgressListener, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.xpBottleRedeemListener, this.plugin);
    }

    private void warmOnlineEconomySessions() {
        for (final Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
            this.economyService.warmPlayerSession(onlinePlayer.getUniqueId(), onlinePlayer.getName());
        }
    }

    private void registerVaultIfPresent() {
        if (!this.plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            return;
        }
        this.vaultEconomyProvider = new WildEconomyVaultProvider(this.plugin, this.economyService, this.economyConfig);
        this.plugin.getServer().getServicesManager().register(
                Economy.class,
                this.vaultEconomyProvider,
                this.plugin,
                ServicePriority.Highest
        );
        this.logActiveVaultEconomyProvider();
    }

    private void registerPlaceholderApiIfPresent() {
        if (!this.plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        this.placeholderExpansion = new WildEconomyExpansion(
                this.plugin,
                this.economyService,
                this.baltopService,
                this.supplierStatsService,
                this.economyConfig
        );
        this.placeholderExpansion.register();
    }

    private void logActiveVaultEconomyProvider() {
        final RegisteredServiceProvider<Economy> registration =
                this.plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (registration == null || registration.getProvider() == null) {
            this.plugin.getLogger().info("Vault economy provider check: no active Economy provider is registered.");
            return;
        }

        final Economy provider = registration.getProvider();
        final String pluginName = registration.getPlugin() != null
                ? registration.getPlugin().getName()
                : "unknown";
        final String providerClass = provider.getClass().getName();

        this.plugin.getLogger().info(
                "Vault economy provider check: active provider is "
                        + providerClass
                        + " from plugin "
                        + pluginName
                        + " at priority "
                        + registration.getPriority()
        );
    }

    public void registerCommands() {
        final ShopOpenSubcommand openSubcommand = new ShopOpenSubcommand(this.shopMenuRouter);
        final ShopSellHandSubcommand sellHandSubcommand = new ShopSellHandSubcommand(this.exchangeService, this.platformExecutor);
        final ShopSellAllSubcommand sellAllSubcommand = new ShopSellAllSubcommand(this.exchangeService, this.platformExecutor);
        final ShopSellContainerSubcommand sellContainerSubcommand = new ShopSellContainerSubcommand(this.foliaContainerSellCoordinator);
        final ShopSellPreviewSubcommand sellPreviewSubcommand = new ShopSellPreviewSubcommand(this.exchangeService, this.platformExecutor);
        final ShopTopSubcommand shopTopSubcommand = new ShopTopSubcommand(this.supplierStatsService);

        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(openSubcommand, sellHandSubcommand, sellAllSubcommand, sellContainerSubcommand, shopTopSubcommand));
        }

        final PluginCommand sell = this.plugin.getCommand("sell");
        if (sell != null) {
            sell.setExecutor(new SellCommand(sellPreviewSubcommand));
        }

        final PluginCommand worth = this.plugin.getCommand("worth");
        if (worth != null) {
            worth.setExecutor(sellPreviewSubcommand);
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

        final PluginCommand shoptop = this.plugin.getCommand("shoptop");
        if (shoptop != null) {
            shoptop.setExecutor(shopTopSubcommand);
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
                new StockTurnoverTask(this.stockTurnoverService),
                this.globalConfig.turnoverIntervalTicks(),
                this.globalConfig.turnoverIntervalTicks()
        );
    }

    public void shutdown() {
        if (this.economyPlayerSessionListener != null) {
            HandlerList.unregisterAll(this.economyPlayerSessionListener);
            this.economyPlayerSessionListener = null;
        }
        if (this.storePlayerSessionListener != null) {
            HandlerList.unregisterAll(this.storePlayerSessionListener);
            this.storePlayerSessionListener = null;
        }
        if (this.xpBottleRedeemListener != null) {
            HandlerList.unregisterAll(this.xpBottleRedeemListener);
            this.xpBottleRedeemListener = null;
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
        if (this.supplierStatsService != null) {
            this.supplierStatsService.shutdown();
            this.supplierStatsService = null;
        }
        if (this.stockService != null) {
            this.stockService.shutdown();
            this.stockService = null;
        }
        if (this.exchangeLayoutBrowseService != null) {
            this.exchangeLayoutBrowseService.shutdown();
            this.exchangeLayoutBrowseService = null;
        }
        if (this.storeRuntimeStateService != null) {
            this.storeRuntimeStateService.shutdown();
            this.storeRuntimeStateService = null;
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
        if (this.placeholderExpansion != null) {
            this.placeholderExpansion.unregister();
            this.placeholderExpansion = null;
        }
        this.exchangeService = null;
        this.exchangeBuyService = null;
        this.exchangeSellService = null;
        this.exchangeBrowseService = null;
        this.stockTurnoverService = null;
        this.foliaContainerSellCoordinator = null;
        this.economyGateway = null;
        this.storeRuntimeStateService = null;
        this.storeService = null;
        this.storeProductsConfig = null;
        this.xpBottleService = null;
    }
}
