package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Load configs
        // Initialize database provider
        // Run migrations
        // Build catalog
        // Create services
    }

    public void registerCommands() {
        // Register /shop and /shopadmin executors
    }

    public void registerTasks() {
        // Register turnover scheduler
    }

    public void shutdown() {
        // Close pools/executors if needed
    }
}
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import org.bukkit.command.PluginCommand;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Load configs
        // Initialize database provider
        // Run migrations
        // Build catalog
        // Create services
    }

    public void registerCommands() {
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand());
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand());
        }
    }

    public void registerTasks() {
        // Register turnover scheduler
    }

    public void shutdown() {
        // Close pools/executors if needed
    }
}
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.VaultEconomyGateway;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.CatalogMergeService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.WorthImporter;
import com.splatage.wild_economy.exchange.item.BukkitItemNormalizer;
import com.splatage.wild_economy.exchange.item.CanonicalItemRules;
import com.splatage.wild_economy.exchange.item.ItemNormalizer;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.item.ItemValidationServiceImpl;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteSchemaVersionRepository;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
    private WorthImportConfig worthImportConfig;
    private ExchangeItemsConfig exchangeItemsConfig;

    private DatabaseProvider databaseProvider;
    private ExchangeCatalog exchangeCatalog;
    private ItemNormalizer itemNormalizer;
    private ItemValidationService itemValidationService;
    private ExchangeStockRepository exchangeStockRepository;
    private ExchangeTransactionRepository exchangeTransactionRepository;
    private EconomyGateway economyGateway;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        final ConfigLoader configLoader = new ConfigLoader(this.plugin);
        this.globalConfig = configLoader.loadGlobalConfig();
        this.databaseConfig = configLoader.loadDatabaseConfig();
        this.worthImportConfig = configLoader.loadWorthImportConfig();
        this.exchangeItemsConfig = configLoader.loadExchangeItemsConfig();

        this.databaseProvider = new DatabaseProvider(this.databaseConfig);

        final SchemaVersionRepository schemaVersionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL wiring not implemented in Commit 2B");
        };

        final MigrationManager migrationManager = new MigrationManager(this.databaseProvider, schemaVersionRepository);
        migrationManager.migrate();

        this.exchangeStockRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL stock repository not implemented in Commit 2B");
        };

        this.exchangeTransactionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL transaction repository not implemented in Commit 2B");
        };

        final WorthImporter worthImporter = new WorthImporter();
        final CatalogMergeService catalogMergeService = new CatalogMergeService();
        final CatalogLoader catalogLoader = new CatalogLoader(worthImporter, catalogMergeService);
        this.exchangeCatalog = catalogLoader.load(this.exchangeItemsConfig, this.worthImportConfig);

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        this.itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        this.itemValidationService = new ItemValidationServiceImpl(this.itemNormalizer, this.exchangeCatalog);

        this.economyGateway = this.resolveVaultEconomy();
    }

    public void registerCommands() {
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand());
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand());
        }
    }

    public void registerTasks() {
        // Turnover task wiring comes later.
    }

    public void shutdown() {
        // No pooled resources yet in Commit 2B.
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }
        return new VaultEconomyGateway(registration.getProvider());
    }
}
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.command.ShopSellAllSubcommand;
import com.splatage.wild_economy.command.ShopSellHandSubcommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.VaultEconomyGateway;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.CatalogMergeService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.WorthImporter;
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
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeStockRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.sqlite.SqliteSchemaVersionRepository;
import com.splatage.wild_economy.exchange.service.ExchangeSellService;
import com.splatage.wild_economy.exchange.service.ExchangeSellServiceImpl;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.exchange.service.ExchangeServiceImpl;
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import com.splatage.wild_economy.exchange.service.TransactionLogServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.exchange.stock.StockServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockStateResolver;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
    private WorthImportConfig worthImportConfig;
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
    private ExchangeSellService exchangeSellService;
    private ExchangeService exchangeService;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        final ConfigLoader configLoader = new ConfigLoader(this.plugin);
        this.globalConfig = configLoader.loadGlobalConfig();
        this.databaseConfig = configLoader.loadDatabaseConfig();
        this.worthImportConfig = configLoader.loadWorthImportConfig();
        this.exchangeItemsConfig = configLoader.loadExchangeItemsConfig();

        this.databaseProvider = new DatabaseProvider(this.databaseConfig);

        final SchemaVersionRepository schemaVersionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL wiring not implemented yet");
        };

        final MigrationManager migrationManager = new MigrationManager(this.databaseProvider, schemaVersionRepository);
        migrationManager.migrate();

        this.exchangeStockRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL stock repository not implemented yet");
        };

        this.exchangeTransactionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL transaction repository not implemented yet");
        };

        final WorthImporter worthImporter = new WorthImporter();
        final CatalogMergeService catalogMergeService = new CatalogMergeService();
        final CatalogLoader catalogLoader = new CatalogLoader(worthImporter, catalogMergeService);
        this.exchangeCatalog = catalogLoader.load(this.exchangeItemsConfig, this.worthImportConfig);

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        this.itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        this.itemValidationService = new ItemValidationServiceImpl(this.itemNormalizer, this.exchangeCatalog);

        this.economyGateway = this.resolveVaultEconomy();

        final StockStateResolver stockStateResolver = new StockStateResolver();
        this.stockService = new StockServiceImpl(this.exchangeStockRepository, this.exchangeCatalog, stockStateResolver);
        this.pricingService = new PricingServiceImpl(this.exchangeCatalog);
        this.transactionLogService = new TransactionLogServiceImpl(this.exchangeTransactionRepository);
        this.exchangeSellService = new ExchangeSellServiceImpl(
            this.exchangeCatalog,
            this.itemValidationService,
            this.stockService,
            this.pricingService,
            this.economyGateway,
            this.transactionLogService
        );
        this.exchangeService = new ExchangeServiceImpl(
            this.exchangeCatalog,
            this.stockService,
            this.exchangeSellService
        );
    }

    public void registerCommands() {
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(
                new ShopSellHandSubcommand(this.exchangeService),
                new ShopSellAllSubcommand(this.exchangeService)
            ));
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand());
        }
    }

    public void registerTasks() {
        // Turnover task wiring comes later.
    }

    public void shutdown() {
        // No pooled resources yet.
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }
        return new VaultEconomyGateway(registration.getProvider());
    }
}
public void registerCommands() {
    final PluginCommand shop = this.plugin.getCommand("shop");
    if (shop != null) {
        shop.setExecutor(new ShopCommand(
            new ShopSellHandSubcommand(this.exchangeService),
            new ShopSellAllSubcommand(this.exchangeService)
        ));
    }

    final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
    if (shopAdmin != null) {
        shopAdmin.setExecutor(new ShopAdminCommand(this.plugin));
    }
}
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.command.ShopOpenSubcommand;
import com.splatage.wild_economy.command.ShopSellAllSubcommand;
import com.splatage.wild_economy.command.ShopSellHandSubcommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.VaultEconomyGateway;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.CatalogMergeService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.WorthImporter;
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
import com.splatage.wild_economy.exchange.service.TransactionLogService;
import com.splatage.wild_economy.exchange.service.TransactionLogServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.exchange.stock.StockServiceImpl;
import com.splatage.wild_economy.exchange.stock.StockStateResolver;
import com.splatage.wild_economy.gui.ExchangeBrowseMenu;
import com.splatage.wild_economy.gui.ExchangeItemDetailMenu;
import com.splatage.wild_economy.gui.ExchangeRootMenu;
import com.splatage.wild_economy.gui.ShopMenuListener;
import com.splatage.wild_economy.gui.ShopMenuRouter;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
    private WorthImportConfig worthImportConfig;
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
    private ExchangeBrowseService exchangeBrowseService;
    private ExchangeBuyService exchangeBuyService;
    private ExchangeSellService exchangeSellService;
    private ExchangeService exchangeService;

    private ShopMenuRouter shopMenuRouter;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        final ConfigLoader configLoader = new ConfigLoader(this.plugin);
        this.globalConfig = configLoader.loadGlobalConfig();
        this.databaseConfig = configLoader.loadDatabaseConfig();
        this.worthImportConfig = configLoader.loadWorthImportConfig();
        this.exchangeItemsConfig = configLoader.loadExchangeItemsConfig();

        this.databaseProvider = new DatabaseProvider(this.databaseConfig);

        final SchemaVersionRepository schemaVersionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL wiring not implemented yet");
        };

        final MigrationManager migrationManager = new MigrationManager(this.databaseProvider, schemaVersionRepository);
        migrationManager.migrate();

        this.exchangeStockRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL stock repository not implemented yet");
        };

        this.exchangeTransactionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL transaction repository not implemented yet");
        };

        final WorthImporter worthImporter = new WorthImporter();
        final CatalogMergeService catalogMergeService = new CatalogMergeService();
        final CatalogLoader catalogLoader = new CatalogLoader(worthImporter, catalogMergeService);
        this.exchangeCatalog = catalogLoader.load(this.exchangeItemsConfig, this.worthImportConfig);

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        this.itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        this.itemValidationService = new ItemValidationServiceImpl(this.itemNormalizer, this.exchangeCatalog);

        this.economyGateway = this.resolveVaultEconomy();

        final StockStateResolver stockStateResolver = new StockStateResolver();
        this.stockService = new StockServiceImpl(this.exchangeStockRepository, this.exchangeCatalog, stockStateResolver);
        this.pricingService = new PricingServiceImpl(this.exchangeCatalog);
        this.transactionLogService = new TransactionLogServiceImpl(this.exchangeTransactionRepository);
        this.exchangeBrowseService = new ExchangeBrowseServiceImpl(this.exchangeCatalog, this.stockService);
        this.exchangeBuyService = new ExchangeBuyServiceImpl(
            this.exchangeCatalog,
            this.itemValidationService,
            this.stockService,
            this.pricingService,
            this.economyGateway,
            this.transactionLogService
        );
        this.exchangeSellService = new ExchangeSellServiceImpl(
            this.exchangeCatalog,
            this.itemValidationService,
            this.stockService,
            this.pricingService,
            this.economyGateway,
            this.transactionLogService
        );
        this.exchangeService = new ExchangeServiceImpl(
            this.exchangeBrowseService,
            this.exchangeBuyService,
            this.exchangeSellService
        );

        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService);
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService, itemDetailMenu);
        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(browseMenu);
        this.shopMenuRouter = new ShopMenuRouter(rootMenu);

        this.plugin.getServer().getPluginManager().registerEvents(
            new ShopMenuListener(rootMenu, browseMenu, itemDetailMenu),
            this.plugin
        );
    }

    public void registerCommands() {
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(
                new ShopOpenSubcommand(this.shopMenuRouter),
                new ShopSellHandSubcommand(this.exchangeService),
                new ShopSellAllSubcommand(this.exchangeService)
            ));
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand(this.plugin));
        }
    }

    public void registerTasks() {
        // Turnover task wiring comes later.
    }

    public void shutdown() {
        // No pooled resources yet.
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }
        return new VaultEconomyGateway(registration.getProvider());
    }
}
