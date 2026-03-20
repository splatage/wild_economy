# wild_economy Folia migration patchset

Source-of-truth snapshot: `8f3a3c63ce62f74b8217604d3639c1bbf76655e2`

This patchset is intentionally conservative.

It does four things:

1. replaces direct Bukkit scheduler use with the Paper/Folia-compatible schedulers,
2. makes GUI session storage safe for concurrent multi-player access,
3. routes menu opens through player-owned scheduling,
4. adds guard wrappers around buy/sell flows so they **fail safely** instead of mutating Bukkit state from the wrong execution context.

This is a **phase-1 Folia migration** for the plugin’s current synchronous API shape. It does **not** redesign `/sellcontainer` into an async or future-based cross-region flow. Instead, it refuses unsafe execution contexts.

---

## File: `src/main/java/com/splatage/wild_economy/platform/PlatformExecutor.java`

```java
package com.splatage.wild_economy.platform;

import org.bukkit.entity.Player;

public interface PlatformExecutor {

    void runOnPlayer(Player player, Runnable task);

    void runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks);

    void cancelPluginTasks();
}
```

---

## File: `src/main/java/com/splatage/wild_economy/platform/PaperFoliaPlatformExecutor.java`

```java
package com.splatage.wild_economy.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PaperFoliaPlatformExecutor implements PlatformExecutor {

    private final Plugin plugin;

    public PaperFoliaPlatformExecutor(final Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void runOnPlayer(final Player player, final Runnable task) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
            return;
        }

        player.getScheduler().run(this.plugin, scheduledTask -> task.run(), null);
    }

    @Override
    public void runGlobalRepeating(final Runnable task, final long initialDelayTicks, final long periodTicks) {
        Objects.requireNonNull(task, "task");

        this.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
            this.plugin,
            scheduledTask -> task.run(),
            initialDelayTicks,
            periodTicks
        );
    }

    @Override
    public void cancelPluginTasks() {
        this.plugin.getServer().getGlobalRegionScheduler().cancelTasks(this.plugin);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/gui/MenuSessionStore.java`

```java
package com.splatage.wild_economy.gui;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MenuSessionStore {

    private final ConcurrentMap<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    public void put(final MenuSession session) {
        Objects.requireNonNull(session, "session");
        this.sessions.put(session.playerId(), session);
    }

    public MenuSession get(final UUID playerId) {
        return this.sessions.get(playerId);
    }

    public void remove(final UUID playerId) {
        this.sessions.remove(playerId);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/FoliaSafeExchangeBuyService.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class FoliaSafeExchangeBuyService implements ExchangeBuyService {

    private final ExchangeBuyService delegate;

    public FoliaSafeExchangeBuyService(final ExchangeBuyService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new BuyResult(
                false,
                itemKey,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                RejectionReason.INTERNAL_ERROR,
                "Player is not online"
            );
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new BuyResult(
                false,
                itemKey,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                RejectionReason.INTERNAL_ERROR,
                "Buy action was attempted off the owning player thread. Please try again."
            );
        }

        return this.delegate.buy(playerId, itemKey, amount);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/FoliaSafeExchangeSellService.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class FoliaSafeExchangeSellService implements ExchangeSellService {

    private static final String OFF_THREAD_MESSAGE =
        "Sell action was attempted off the owning player thread. Please try again.";

    private static final String CROSS_REGION_CONTAINER_MESSAGE =
        "Container selling is not available from this execution context. Move away from region borders and try again.";

    private final ExchangeSellService delegate;

    public FoliaSafeExchangeSellService(final ExchangeSellService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, OFF_THREAD_MESSAGE);
        }

        return this.delegate.sellHand(playerId);
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.of(), OFF_THREAD_MESSAGE);
        }

        return this.delegate.sellAll(playerId);
    }

    @Override
    public SellContainerResult sellContainer(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), null, "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), null, OFF_THREAD_MESSAGE);
        }

        if (!Bukkit.isOwnedByCurrentRegion(player.getLocation(), 1)) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                null,
                CROSS_REGION_CONTAINER_MESSAGE
            );
        }

        return this.delegate.sellContainer(playerId);
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
            this.transactionLogService
        );

        final ExchangeSellService rawSellService = new ExchangeSellServiceImpl(
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

        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(this.exchangeService);
        final ExchangeSubcategoryMenu subcategoryMenu = new ExchangeSubcategoryMenu(this.exchangeService);
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService);
        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService);

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

        this.plugin.getServer().getPluginManager().registerEvents(
            new ShopMenuListener(rootMenu, subcategoryMenu, browseMenu, itemDetailMenu, this.shopMenuRouter),
            this.plugin
        );
    }

    public void registerCommands() {
        final ShopOpenSubcommand openSubcommand = new ShopOpenSubcommand(this.shopMenuRouter);
        final ShopSellHandSubcommand sellHandSubcommand = new ShopSellHandSubcommand(this.exchangeService);
        final ShopSellAllSubcommand sellAllSubcommand = new ShopSellAllSubcommand(this.exchangeService);
        final ShopSellContainerSubcommand sellContainerSubcommand = new ShopSellContainerSubcommand(this.exchangeService);

        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(
                openSubcommand,
                sellHandSubcommand,
                sellAllSubcommand,
                sellContainerSubcommand
            ));
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
        this.platformExecutor.cancelPluginTasks();

        if (this.transactionLogService != null) {
            this.transactionLogService.shutdown();
        }

        if (this.stockService != null) {
            this.stockService.shutdown();
        }

        if (this.databaseProvider != null) {
            this.databaseProvider.close();
        }
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration =
            this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }
        return new VaultEconomyGateway(registration.getProvider());
    }
}
```

---

## File: `src/main/resources/plugin.yml`

```yaml
name: wild_economy
version: ${version}
main: com.splatage.wild_economy.WildEconomyPlugin
api-version: '1.21'
folia-supported: true
author: splatage
description: Curated Exchange-first economy plugin for Minecraft

depend:
  - Vault

softdepend:
  - Essentials

commands:
  shop:
    description: Open the shop or use sell commands.
    usage: /shop [sellhand|sellall|sellcontainer]
    aliases: [exchange]
    permission: wild_economy.shop
  sellhand:
    description: Sell the item in your hand to the Exchange.
    usage: /sellhand
    permission: wild_economy.shop.sell
  sellall:
    description: Sell all sellable items in your inventory to the Exchange.
    usage: /sellall
    permission: wild_economy.shop.sell
  sellcontainer:
    description: Sell the contents of a looked-at container or held shulker box.
    usage: /sellcontainer
    permission: wild_economy.shop.sellcontainer
  shopadmin:
    description: Admin commands for wild_economy.
    usage: /shopadmin
    permission: wild_economy.admin

permissions:
  wild_economy.shop:
    description: Allows use of player shop commands.
    default: true
  wild_economy.shop.sell:
    description: Allows selling to the Exchange.
    default: true
  wild_economy.shop.sellcontainer:
    description: Allows selling the contents of supported containers.
    default: true
  wild_economy.shop.buy:
    description: Allows buying from the Exchange.
    default: true
  wild_economy.admin:
    description: Allows administrative use of wild_economy.
    default: op
```

---

## Notes

* I left `ExchangeBuyServiceImpl` and `ExchangeSellServiceImpl` themselves untouched in this phase and wrapped them instead. That keeps the migration narrow and reduces drift against the current branch.
* The `FoliaSafeExchangeSellService` wrapper deliberately rejects `/sellcontainer` when the current thread does not own the player’s surrounding chunk square. That is a conservative safety fence for the existing synchronous container-selling design.
* This patchset is designed around the current repo shape at `8f3a3c63ce62f74b8217604d3639c1bbf76655e2`.
