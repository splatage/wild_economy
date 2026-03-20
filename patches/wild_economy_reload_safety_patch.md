# wild_economy reload safety patch

Target repo state reviewed against `main` at commit `4204606e181a2f0defab2adcc48dc46964d23c96`.

This patch focuses only on reload safety:

- prevent duplicated `ShopMenuListener` registration across `/shopadmin reload`
- explicitly unregister the old listener during shutdown
- close stale open shop views during shutdown/reload
- clear live menu sessions associated with the old router for online players
- make bootstrap lifecycle methods synchronized and cleaner
- make `/shopadmin reload` report reload failure cleanly to the sender

---

## File: `src/main/java/com/splatage/wild_economy/bootstrap/PluginBootstrap.java`

```java
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;

public final class PluginBootstrap {

    private final WildEconomyPlugin plugin;
    private final Object lifecycleLock = new Object();

    private ServiceRegistry services;

    public PluginBootstrap(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        synchronized (this.lifecycleLock) {
            if (this.services != null) {
                return;
            }
            this.services = this.startServices();
        }
    }

    public void reload() {
        synchronized (this.lifecycleLock) {
            final ServiceRegistry current = this.services;
            this.services = null;

            if (current != null) {
                current.shutdown();
            }

            this.services = this.startServices();
        }
    }

    public void disable() {
        synchronized (this.lifecycleLock) {
            final ServiceRegistry current = this.services;
            this.services = null;

            if (current != null) {
                current.shutdown();
            }
        }
    }

    private ServiceRegistry startServices() {
        final ServiceRegistry registry = new ServiceRegistry(this.plugin);
        registry.initialize();
        registry.registerCommands();
        registry.registerTasks();
        return registry;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

```java
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.command.ShopOpenSubcommand;
import com.splatage.wild_economy.command.ShopSellAllSubcommand;
import com.splatage.wild_economy.command.ShopSellContainerSubcommand;
import com.splatage.wild_economy.command.ShopSellHandSubcommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.VaultEconomyGateway;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.CatalogMergeService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.GeneratedCatalogImporter;
import com.splatage.wild_economy.exchange.catalog.RootValueImporter;
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
import com.splatage.wild_economy.gui.MenuSessionStore;
import com.splatage.wild_economy.gui.ShopMenuListener;
import com.splatage.wild_economy.gui.ShopMenuRouter;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import com.splatage.wild_economy.platform.PaperFoliaPlatformExecutor;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.io.File;
import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredServiceProvider;

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
    private FoliaContainerSellCoordinator foliaContainerSellCoordinator;

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
        this.platformExecutor = new PaperFoliaPlatformExecutor(plugin);
    }

    public void initialize() {
        final ConfigLoader configLoader = new ConfigLoader(this.plugin);
        this.globalConfig = configLoader.loadGlobalConfig();
        this.databaseConfig = configLoader.loadDatabaseConfig();
        this.exchangeItemsConfig = configLoader.loadExchangeItemsConfig();

        this.databaseProvider = new DatabaseProvider(this.databaseConfig);

        final SchemaVersionRepository schemaVersionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(this.databaseProvider);
            case MYSQL -> new MysqlSchemaVersionRepository(this.databaseProvider);
        };

        final MigrationManager migrationManager = new MigrationManager(this.databaseProvider, schemaVersionRepository);
        migrationManager.migrate();

        this.exchangeStockRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(this.databaseProvider);
            case MYSQL -> new MysqlExchangeStockRepository(this.databaseProvider);
        };

        this.exchangeTransactionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(this.databaseProvider);
            case MYSQL -> new MysqlExchangeTransactionRepository(this.databaseProvider);
        };

        final File rootValuesFile = new File(this.plugin.getDataFolder(), "root-values.yml");
        final File generatedCatalogFile = new File(new File(this.plugin.getDataFolder(), "generated"), "generated-catalog.yml");

        if (!generatedCatalogFile.exists()) {
            this.plugin.getLogger().warning(
                "generated/generated-catalog.yml not found. Runtime catalog will fall back to exchange-items.yml overrides only."
            );
        }

        final GeneratedCatalogImporter generatedCatalogImporter = new GeneratedCatalogImporter();
        final RootValueImporter rootValueImporter = new RootValueImporter();
        final CatalogMergeService catalogMergeService = new CatalogMergeService();
        final CatalogLoader catalogLoader = new CatalogLoader(
            generatedCatalogImporter,
            rootValueImporter,
            catalogMergeService
        );

        this.exchangeCatalog = Objects.requireNonNull(
            catalogLoader.load(this.exchangeItemsConfig, rootValuesFile, generatedCatalogFile),
            "exchangeCatalog"
        );

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        this.itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        this.itemValidationService = new ItemValidationServiceImpl(this.itemNormalizer, this.exchangeCatalog);
        this.economyGateway = this.resolveVaultEconomy();

        final StockStateResolver stockStateResolver = new StockStateResolver();
        this.stockService = new StockServiceImpl(
            this.exchangeStockRepository,
            this.exchangeCatalog,
            stockStateResolver,
            this.plugin.getLogger(),
            this.databaseProvider.dialect(),
            this.databaseConfig.mysqlMaximumPoolSize()
        );

        this.pricingService = new PricingServiceImpl(this.exchangeCatalog);
        this.transactionLogService = new TransactionLogServiceImpl(
            this.exchangeTransactionRepository,
            this.plugin.getLogger(),
            this.databaseProvider.dialect(),
            this.databaseConfig.mysqlMaximumPoolSize()
        );
        this.stockTurnoverService = new StockTurnoverServiceImpl(
            this.exchangeCatalog,
            this.stockService,
            this.transactionLogService
        );
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
        this.exchangeService = new ExchangeServiceImpl(
            this.exchangeBrowseService,
            this.exchangeBuyService,
            this.exchangeSellService
        );
        this.foliaContainerSellCoordinator = new FoliaContainerSellCoordinator(
            this.platformExecutor,
            this.exchangeService,
            rawSellService
        );

        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(this.exchangeService);
        final ExchangeSubcategoryMenu subcategoryMenu = new ExchangeSubcategoryMenu(this.exchangeService);
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService);
        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService, this.platformExecutor);

        this.shopMenuRouter = new ShopMenuRouter(
            this.platformExecutor,
            new MenuSessionStore(),
            rootMenu,
            subcategoryMenu,
            browseMenu,
            itemDetailMenu
        );

        rootMenu.setShopMenuRouter(this.shopMenuRouter);
        subcategoryMenu.setShopMenuRouter(this.shopMenuRouter);
        browseMenu.setShopMenuRouter(this.shopMenuRouter);
        itemDetailMenu.setShopMenuRouter(this.shopMenuRouter);

        this.shopMenuListener = new ShopMenuListener(
            rootMenu,
            subcategoryMenu,
            browseMenu,
            itemDetailMenu,
            this.shopMenuRouter
        );
        this.plugin.getServer().getPluginManager().registerEvents(this.shopMenuListener, this.plugin);
    }

    public void registerCommands() {
        final ShopOpenSubcommand openSubcommand = new ShopOpenSubcommand(this.shopMenuRouter);
        final ShopSellHandSubcommand sellHandSubcommand = new ShopSellHandSubcommand(this.exchangeService, this.platformExecutor);
        final ShopSellAllSubcommand sellAllSubcommand = new ShopSellAllSubcommand(this.exchangeService, this.platformExecutor);
        final ShopSellContainerSubcommand sellContainerSubcommand = new ShopSellContainerSubcommand(
            this.foliaContainerSellCoordinator
        );

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
            shopAdmin.setExecutor(new ShopAdminCommand(this.plugin));
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
        if (this.shopMenuListener != null) {
            HandlerList.unregisterAll(this.shopMenuListener);
            this.shopMenuListener = null;
        }

        if (this.shopMenuRouter != null) {
            this.shopMenuRouter.closeAllShopViews();
            this.shopMenuRouter = null;
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
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration = this.plugin.getServer()
            .getServicesManager()
            .getRegistration(Economy.class);

        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }

        return new VaultEconomyGateway(registration.getProvider());
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuRouter.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ShopMenuRouter {

    private final PlatformExecutor platformExecutor;
    private final MenuSessionStore menuSessionStore;
    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;

    public ShopMenuRouter(
        final PlatformExecutor platformExecutor,
        final MenuSessionStore menuSessionStore,
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.menuSessionStore = Objects.requireNonNull(menuSessionStore, "menuSessionStore");
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    public void openRoot(final Player player) {
        this.menuSessionStore.put(new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.ROOT,
            null,
            null,
            0,
            null,
            false
        ));
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeRootMenu.open(player));
    }

    public void openSubcategory(final Player player, final ItemCategory category) {
        this.menuSessionStore.put(new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.SUBCATEGORY,
            category,
            null,
            0,
            null,
            false
        ));
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeSubcategoryMenu.open(player, category));
    }

    public void openBrowse(
        final Player player,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        this.menuSessionStore.put(new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.BROWSE,
            category,
            generatedCategory,
            page,
            null,
            viaSubcategory
        ));
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.exchangeBrowseMenu.open(player, category, generatedCategory, page, viaSubcategory)
        );
    }

    public void openDetail(final Player player, final ItemKey itemKey) {
        final MenuSession previous = this.menuSessionStore.get(player.getUniqueId());
        final ItemCategory category = previous == null ? null : previous.currentCategory();
        final GeneratedItemCategory generatedCategory = previous == null ? null : previous.currentGeneratedCategory();
        final int page = previous == null ? 0 : previous.currentPage();
        final boolean viaSubcategory = previous != null && previous.viaSubcategory();

        this.menuSessionStore.put(new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.DETAIL,
            category,
            generatedCategory,
            page,
            itemKey,
            viaSubcategory
        ));
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeItemDetailMenu.open(player, itemKey, 1));
    }

    public void goBack(final Player player) {
        final MenuSession session = this.menuSessionStore.get(player.getUniqueId());
        if (session == null) {
            this.openRoot(player);
            return;
        }

        switch (session.viewType()) {
            case ROOT -> this.openRoot(player);
            case SUBCATEGORY -> this.openRoot(player);
            case BROWSE -> {
                if (session.viaSubcategory() && session.currentCategory() != null) {
                    this.openSubcategory(player, session.currentCategory());
                } else {
                    this.openRoot(player);
                }
            }
            case DETAIL -> {
                if (session.currentCategory() == null) {
                    this.openRoot(player);
                } else {
                    this.openBrowse(
                        player,
                        session.currentCategory(),
                        session.currentGeneratedCategory(),
                        session.currentPage(),
                        session.viaSubcategory()
                    );
                }
            }
        }
    }

    public MenuSession getSession(final UUID playerId) {
        return this.menuSessionStore.get(playerId);
    }

    public void clearSession(final UUID playerId) {
        this.menuSessionStore.remove(playerId);
    }

    public void closeAllShopViews() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            this.clearSession(player.getUniqueId());

            final String title = player.getOpenInventory().getTitle();
            if (!isShopViewTitle(title)) {
                continue;
            }

            this.platformExecutor.runOnPlayer(player, player::closeInventory);
        }
    }

    public static boolean isShopViewTitle(final String title) {
        return title != null
            && (title.equals("Shop") || title.startsWith("Shop - ") || title.startsWith("Buy - "));
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuListener.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ShopMenuListener implements Listener {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final ShopMenuRouter shopMenuRouter;

    public ShopMenuListener(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu,
        final ShopMenuRouter shopMenuRouter
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (!ShopMenuRouter.isShopViewTitle(title)) {
            return;
        }

        // Always cancel clicks in shop-managed inventories first.
        event.setCancelled(true);

        final MenuSession session = this.shopMenuRouter.getSession(event.getWhoClicked().getUniqueId());
        if (session == null) {
            if ("Shop".equals(title)) {
                this.exchangeRootMenu.handleClick(event);
            }
            return;
        }

        switch (session.viewType()) {
            case ROOT -> this.exchangeRootMenu.handleClick(event);
            case SUBCATEGORY -> {
                if (session.currentCategory() != null) {
                    this.exchangeSubcategoryMenu.handleClick(event, session.currentCategory());
                }
            }
            case BROWSE -> {
                if (session.currentCategory() != null) {
                    this.exchangeBrowseMenu.handleClick(
                        event,
                        session.currentCategory(),
                        session.currentGeneratedCategory(),
                        session.currentPage(),
                        session.viaSubcategory()
                    );
                }
            }
            case DETAIL -> {
                if (session.currentItemKey() != null) {
                    this.exchangeItemDetailMenu.handleClick(event, session.currentItemKey());
                    return;
                }

                final var current = event.getInventory().getItem(11);
                if (current != null && current.getType() != Material.AIR) {
                    this.exchangeItemDetailMenu.handleClick(event, this.toItemKey(current.getType()));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.shopMenuRouter.clearSession(event.getPlayer().getUniqueId());
    }

    private ItemKey toItemKey(final Material material) {
        return new ItemKey("minecraft:" + material.name().toLowerCase());
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/command/ShopAdminCommand.java`

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.generate.CatalogGenerationReportFormatter;
import com.splatage.wild_economy.catalog.generate.CatalogGeneratorFacade;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopAdminCommand implements CommandExecutor {

    private final WildEconomyPlugin plugin;

    public ShopAdminCommand(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Use /shopadmin <reload|generatecatalog>");
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "reload" -> this.handleReload(sender);
            case "generatecatalog" -> this.handleGenerateCatalog(sender);
            default -> {
                sender.sendMessage("Unknown admin subcommand. Use /shopadmin <reload|generatecatalog>");
                yield true;
            }
        };
    }

    private boolean handleReload(final CommandSender sender) {
        try {
            this.plugin.reloadConfig();
            this.plugin.getBootstrap().reload();
            sender.sendMessage("wild_economy reloaded.");
        } catch (final RuntimeException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to reload wild_economy", exception);
            sender.sendMessage("Reload failed. Check console for details.");
        }
        return true;
    }

    private boolean handleGenerateCatalog(final CommandSender sender) {
        final File rootValuesFile = new File(this.plugin.getDataFolder(), "root-values.yml");
        if (!rootValuesFile.exists() || !rootValuesFile.isFile()) {
            sender.sendMessage("Catalog generation aborted: root-values.yml not found at " + rootValuesFile.getPath());
            return true;
        }

        try {
            final CatalogGeneratorFacade facade = new CatalogGeneratorFacade(this.plugin);
            final CatalogGenerationResult result = facade.generateFromRootValuesFile(rootValuesFile);
            facade.writeOutputs(result);

            final File generatedDir = new File(this.plugin.getDataFolder(), "generated");
            sender.sendMessage("Generated catalog files in " + generatedDir.getPath());
            sender.sendMessage(CatalogGenerationReportFormatter.formatSingleLine(result));
            return true;
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to generate catalog data", exception);
            sender.sendMessage("Failed to generate catalog data: " + exception.getMessage());
            return true;
        }
    }
}
```
