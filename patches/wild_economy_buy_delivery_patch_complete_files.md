# wild_economy buy delivery priority patch

Patched against live commit `5e5210ab9f55158133bbf8911115f9213423a79a`.

This patch adds configurable purchase delivery priority in this order:

1. Held shulker
2. Looked-at chest/container
3. Player inventory
4. Drop at feet

All delivery steps are opt-in config options.

---

## File: `src/main/java/com/splatage/wild_economy/config/GlobalConfig.java`

```java
package com.splatage.wild_economy.config;

public record GlobalConfig(
    long turnoverIntervalTicks,
    int guiPageSize,
    String baseCommand,
    String adminCommand,
    boolean debugLogging,
    boolean buyToHeldShulkerEnabled,
    boolean buyToLookedAtContainerEnabled,
    boolean buyToInventoryEnabled,
    boolean buyDropAtFeetEnabled
) {}

```

---

## File: `src/main/java/com/splatage/wild_economy/config/ConfigLoader.java`

```java
package com.splatage.wild_economy.config;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigLoader {

    private final WildEconomyPlugin plugin;

    public ConfigLoader(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public GlobalConfig loadGlobalConfig() {
        final FileConfiguration config = this.plugin.getConfig();
        return new GlobalConfig(
            config.getLong("turnover.interval-ticks", 72000L),
            config.getInt("gui.page-size", 45),
            config.getString("commands.base-command", "shop"),
            config.getString("commands.admin-command", "shopadmin"),
            config.getBoolean("logging.debug", false),
            config.getBoolean("buy-delivery.use-held-shulker", false),
            config.getBoolean("buy-delivery.use-looked-at-container", false),
            config.getBoolean("buy-delivery.use-player-inventory", true),
            config.getBoolean("buy-delivery.drop-at-feet", false)
        );
    }

    public DatabaseConfig loadDatabaseConfig() {
        final FileConfiguration config = this.loadYaml("database.yml");
        return new DatabaseConfig(
            config.getString("backend", "sqlite"),
            config.getString("sqlite.file", "plugins/wild_economy/data.db"),
            config.getString("mysql.host", "127.0.0.1"),
            config.getInt("mysql.port", 3306),
            config.getString("mysql.database", "wild_economy"),
            config.getString("mysql.username", "root"),
            config.getString("mysql.password", "change-me"),
            config.getBoolean("mysql.ssl", false),
            config.getInt("mysql.maximum-pool-size", 10)
        );
    }

    public ExchangeItemsConfig loadExchangeItemsConfig() {
        final FileConfiguration config = this.loadYaml("exchange-items.yml");
        final ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            throw new IllegalStateException("exchange-items.yml is missing the 'items' section");
        }

        final Map<ItemKey, ExchangeItemsConfig.RawItemEntry> exactEntries = new LinkedHashMap<>();
        final List<WildcardItemRule> wildcardRules = new ArrayList<>();

        int order = 0;
        for (final String rawItemKey : itemsSection.getKeys(false)) {
            final ConfigurationSection section = itemsSection.getConfigurationSection(rawItemKey);
            if (section == null) {
                continue;
            }

            final String normalizedItemKey = this.normalizeItemKey(rawItemKey);
            final RawItemSpec spec = this.parseRawItemSpec(section);

            if (this.isWildcardPattern(normalizedItemKey)) {
                wildcardRules.add(new WildcardItemRule(
                    normalizedItemKey,
                    this.compileGlob(normalizedItemKey),
                    this.wildcardSpecificity(normalizedItemKey),
                    order++,
                    spec
                ));
                continue;
            }

            final ItemKey itemKey = new ItemKey(normalizedItemKey);
            exactEntries.put(itemKey, this.toRawItemEntry(itemKey, spec));
            order++;
        }

        final Map<ItemKey, ExchangeItemsConfig.RawItemEntry> expandedEntries = new LinkedHashMap<>();

        wildcardRules.sort(
            Comparator.comparingInt(WildcardItemRule::specificity)
                .thenComparingInt(WildcardItemRule::order)
        );

        for (final WildcardItemRule rule : wildcardRules) {
            for (final Material material : Material.values()) {
                if (!this.isIncludedMaterial(material)) {
                    continue;
                }

                final String normalizedItemKey = this.normalizeItemKey(material);
                if (!rule.matches(normalizedItemKey)) {
                    continue;
                }

                final ItemKey itemKey = new ItemKey(normalizedItemKey);
                expandedEntries.put(itemKey, this.toRawItemEntry(itemKey, rule.spec()));
            }
        }

        expandedEntries.putAll(exactEntries);
        return new ExchangeItemsConfig(expandedEntries);
    }

    private RawItemSpec parseRawItemSpec(final ConfigurationSection section) {
        final String displayName = section.contains("display-name") ? section.getString("display-name") : null;
        final ItemCategory category = this.parseTopLevelCategory(section.getString("category", null));
        final GeneratedItemCategory generatedCategory = this.parseGeneratedCategory(section.getString("generated-category", null));
        final ItemPolicyMode policyMode = ItemPolicyMode.valueOf(
            section.getString("policy", "DISABLED").toUpperCase(Locale.ROOT)
        );

        final boolean buyEnabled = section.getBoolean("buy-enabled", false);
        final boolean sellEnabled = section.getBoolean("sell-enabled", false);
        final long stockCap = section.getLong("stock-cap", 0L);
        final long turnoverAmountPerInterval = section.getLong("turnover-amount-per-interval", 0L);
        final BigDecimal buyPrice = this.getBigDecimal(section, "buy-price");
        final BigDecimal sellPrice = this.getBigDecimal(section, "sell-price");
        final List<SellPriceBand> sellPriceBands = this.parseSellPriceBands(section.getMapList("sell-price-bands"));

        return new RawItemSpec(
            displayName,
            category,
            generatedCategory,
            policyMode,
            buyEnabled,
            sellEnabled,
            stockCap,
            turnoverAmountPerInterval,
            buyPrice,
            sellPrice,
            sellPriceBands
        );
    }

    private ItemCategory parseTopLevelCategory(final String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return null;
        }
        final String normalized = rawCategory.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "FARMING", "FOOD", "FARMING_AND_FOOD" -> ItemCategory.FARMING_AND_FOOD;
            case "MINING", "ORES_AND_MINERALS", "MINING_AND_MINERALS" -> ItemCategory.MINING_AND_MINERALS;
            case "MOB_DROPS" -> ItemCategory.MOB_DROPS;
            case "BUILDING", "WOODS", "STONE", "BUILDING_MATERIALS" -> ItemCategory.BUILDING_MATERIALS;
            case "UTILITY", "REDSTONE", "TOOLS", "BREWING", "TRANSPORT", "REDSTONE_AND_UTILITY" -> ItemCategory.REDSTONE_AND_UTILITY;
            case "COMBAT", "NETHER", "END", "COMBAT_AND_ADVENTURE" -> ItemCategory.COMBAT_AND_ADVENTURE;
            case "DECORATION", "MISC" -> ItemCategory.MISC;
            default -> null;
        };
    }

    private GeneratedItemCategory parseGeneratedCategory(final String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return null;
        }
        final String normalized = rawCategory.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "FARMING" -> GeneratedItemCategory.FARMING;
            case "FOOD" -> GeneratedItemCategory.FOOD;
            case "ORES_AND_MINERALS" -> GeneratedItemCategory.ORES_AND_MINERALS;
            case "MOB_DROPS" -> GeneratedItemCategory.MOB_DROPS;
            case "WOODS" -> GeneratedItemCategory.WOODS;
            case "STONE" -> GeneratedItemCategory.STONE;
            case "REDSTONE" -> GeneratedItemCategory.REDSTONE;
            case "TOOLS" -> GeneratedItemCategory.TOOLS;
            case "BREWING" -> GeneratedItemCategory.BREWING;
            case "TRANSPORT" -> GeneratedItemCategory.TRANSPORT;
            case "COMBAT" -> GeneratedItemCategory.COMBAT;
            case "NETHER" -> GeneratedItemCategory.NETHER;
            case "END" -> GeneratedItemCategory.END;
            case "DECORATION" -> GeneratedItemCategory.DECORATION;
            case "MISC" -> GeneratedItemCategory.MISC;
            default -> null;
        };
    }

    private ExchangeItemsConfig.RawItemEntry toRawItemEntry(
        final ItemKey itemKey,
        final RawItemSpec spec
    ) {
        return new ExchangeItemsConfig.RawItemEntry(
            itemKey,
            spec.displayName(),
            spec.category(),
            spec.generatedCategory(),
            spec.policyMode(),
            spec.buyEnabled(),
            spec.sellEnabled(),
            spec.stockCap(),
            spec.turnoverAmountPerInterval(),
            spec.buyPrice(),
            spec.sellPrice(),
            spec.sellPriceBands()
        );
    }

    private List<SellPriceBand> parseSellPriceBands(final List<Map<?, ?>> rawBands) {
        final List<SellPriceBand> bands = new ArrayList<>();
        for (final Map<?, ?> rawBand : rawBands) {
            final double minFill = this.asDouble(rawBand.get("min-fill"), 0.0D);
            final double maxFill = this.asDouble(rawBand.get("max-fill"), 1.0D);
            final BigDecimal multiplier = this.asBigDecimal(rawBand.get("multiplier"));
            bands.add(new SellPriceBand(minFill, maxFill, multiplier));
        }
        return List.copyOf(bands);
    }

    private BigDecimal getBigDecimal(final ConfigurationSection section, final String path) {
        if (!section.contains(path)) {
            return null;
        }
        return this.asBigDecimal(section.get(path));
    }

    private BigDecimal asBigDecimal(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private double asDouble(final Object value, final double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private FileConfiguration loadYaml(final String resourceName) {
        final File file = new File(this.plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            this.plugin.saveResource(resourceName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private boolean isWildcardPattern(final String itemKey) {
        return itemKey.indexOf('*') >= 0;
    }

    private int wildcardSpecificity(final String itemKey) {
        int specificity = 0;
        for (int i = 0; i < itemKey.length(); i++) {
            if (itemKey.charAt(i) != '*') {
                specificity++;
            }
        }
        return specificity;
    }

    private Pattern compileGlob(final String glob) {
        final StringBuilder regex = new StringBuilder(glob.length() * 2);
        regex.append('^');
        for (int i = 0; i < glob.length(); i++) {
            final char ch = glob.charAt(i);
            if (ch == '*') {
                regex.append(".*");
                continue;
            }
            if ("\\.^$|?+()[]{}".indexOf(ch) >= 0) {
                regex.append('\\');
            }
            regex.append(ch);
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    private boolean isIncludedMaterial(final Material material) {
        if (material == Material.AIR) {
            return false;
        }
        if (!material.isItem()) {
            return false;
        }
        return !material.isLegacy();
    }

    private String normalizeItemKey(final String rawItemKey) {
        String key = rawItemKey.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        return key;
    }

    private String normalizeItemKey(final Material material) {
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
    }

    private record RawItemSpec(
        String displayName,
        ItemCategory category,
        GeneratedItemCategory generatedCategory,
        ItemPolicyMode policyMode,
        boolean buyEnabled,
        boolean sellEnabled,
        long stockCap,
        long turnoverAmountPerInterval,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        List<SellPriceBand> sellPriceBands
    ) {
    }

    private record WildcardItemRule(
        String pattern,
        Pattern regex,
        int specificity,
        int order,
        RawItemSpec spec
    ) {
        private boolean matches(final String itemKey) {
            return this.regex.matcher(itemKey).matches();
        }
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

        this.plugin.getServer().getPluginManager().registerEvents(
            new ShopMenuListener(rootMenu, subcategoryMenu, browseMenu, itemDetailMenu, this.shopMenuRouter),
            this.plugin
        );
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

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.EconomyResult;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.item.ValidationResult;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.integration.protection.ContainerAccessResult;
import com.splatage.wild_economy.integration.protection.ContainerAccessService;
import com.splatage.wild_economy.integration.protection.ContainerAccessServices;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Lockable;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public final class ExchangeBuyServiceImpl implements ExchangeBuyService {

    private static final int MAX_BUY_AMOUNT = 64;
    private static final int CONTAINER_TARGET_RANGE = 5;
    private static final Logger LOGGER = Logger.getLogger(ExchangeBuyServiceImpl.class.getName());
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final ExchangeCatalog exchangeCatalog;
    private final ItemValidationService itemValidationService;
    private final StockService stockService;
    private final PricingService pricingService;
    private final EconomyGateway economyGateway;
    private final TransactionLogService transactionLogService;
    private final GlobalConfig globalConfig;
    private final ContainerAccessService containerAccessService;

    public ExchangeBuyServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final ItemValidationService itemValidationService,
        final StockService stockService,
        final PricingService pricingService,
        final EconomyGateway economyGateway,
        final TransactionLogService transactionLogService,
        final GlobalConfig globalConfig
    ) {
        this(
            exchangeCatalog,
            itemValidationService,
            stockService,
            pricingService,
            economyGateway,
            transactionLogService,
            globalConfig,
            ContainerAccessServices.createDefault()
        );
    }

    ExchangeBuyServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final ItemValidationService itemValidationService,
        final StockService stockService,
        final PricingService pricingService,
        final EconomyGateway economyGateway,
        final TransactionLogService transactionLogService,
        final GlobalConfig globalConfig,
        final ContainerAccessService containerAccessService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.itemValidationService = Objects.requireNonNull(itemValidationService, "itemValidationService");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.economyGateway = Objects.requireNonNull(economyGateway, "economyGateway");
        this.transactionLogService = Objects.requireNonNull(transactionLogService, "transactionLogService");
        this.globalConfig = Objects.requireNonNull(globalConfig, "globalConfig");
        this.containerAccessService = Objects.requireNonNull(containerAccessService, "containerAccessService");
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new BuyResult(false, itemKey, 0, null, null, RejectionReason.INTERNAL_ERROR, "Player is not online");
        }

        if (amount <= 0 || amount > MAX_BUY_AMOUNT) {
            return new BuyResult(
                false,
                itemKey,
                0,
                null,
                null,
                RejectionReason.BUY_NOT_ALLOWED,
                "Amount must be between 1 and " + MAX_BUY_AMOUNT
            );
        }

        if (!this.hasAnyEnabledDeliveryTarget()) {
            return new BuyResult(
                false,
                itemKey,
                0,
                null,
                null,
                RejectionReason.BUY_NOT_ALLOWED,
                "No buy delivery destination is enabled in config"
            );
        }

        final ValidationResult validation = this.itemValidationService.validateForBuy(itemKey);
        if (!validation.valid()) {
            return new BuyResult(false, itemKey, 0, null, null, validation.rejectionReason(), validation.detail());
        }

        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final boolean playerStocked = entry.policyMode() == ItemPolicyMode.PLAYER_STOCKED;
        final StockSnapshot snapshot = this.stockService.getSnapshot(itemKey);
        if (playerStocked && snapshot.stockCount() < amount) {
            return new BuyResult(false, itemKey, 0, null, null, RejectionReason.OUT_OF_STOCK, "Not enough stock available");
        }

        final BuyQuote quote = this.pricingService.quoteBuy(itemKey, amount, snapshot);
        final BigDecimal balance = this.economyGateway.getBalance(playerId);
        if (balance.compareTo(quote.totalPrice()) < 0) {
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.INSUFFICIENT_FUNDS,
                "Not enough money"
            );
        }

        final Material material = Material.matchMaterial(itemKey.value().replace("minecraft:", "").toUpperCase());
        if (material == null || material == Material.AIR) {
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.INTERNAL_ERROR,
                "Invalid material mapping"
            );
        }

        if (playerStocked && !this.stockService.tryConsume(itemKey, amount)) {
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.OUT_OF_STOCK,
                "Not enough stock available"
            );
        }

        final EconomyResult withdrawal = this.economyGateway.withdraw(playerId, quote.totalPrice());
        if (!withdrawal.success()) {
            if (playerStocked) {
                this.stockService.addStock(itemKey, amount);
            }
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.INTERNAL_ERROR,
                withdrawal.message()
            );
        }

        final DeliveryOutcome delivery = this.deliverPurchase(player, material, amount);
        final int amountBought = delivery.amountDelivered();
        final int undeliveredAmount = delivery.amountUndelivered();

        if (undeliveredAmount > 0 && playerStocked) {
            this.stockService.addStock(itemKey, undeliveredAmount);
        }

        if (amountBought <= 0) {
            final EconomyResult refundResult = this.economyGateway.deposit(playerId, quote.totalPrice());
            if (!refundResult.success()) {
                LOGGER.log(
                    Level.SEVERE,
                    "Failed to automatically refund player "
                        + playerId
                        + " after an undeliverable purchase of "
                        + amount
                        + "x "
                        + itemKey.value()
                        + ". Charged="
                        + quote.totalPrice()
                        + ", reason="
                        + refundResult.message()
                );
                return new BuyResult(
                    false,
                    itemKey,
                    0,
                    quote.unitPrice(),
                    quote.totalPrice(),
                    RejectionReason.INTERNAL_ERROR,
                    "Purchase failed after payment and the automatic refund also failed. No items were delivered. Please contact staff."
                );
            }

            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                ZERO_MONEY,
                RejectionReason.INVENTORY_FULL,
                "No enabled delivery destination had space for this purchase"
            );
        }

        final BigDecimal actualTotalPrice = quote.unitPrice()
            .multiply(BigDecimal.valueOf(amountBought))
            .setScale(2, RoundingMode.HALF_UP);

        if (undeliveredAmount > 0) {
            final BigDecimal refund = quote.totalPrice().subtract(actualTotalPrice).setScale(2, RoundingMode.HALF_UP);
            if (refund.compareTo(BigDecimal.ZERO) > 0) {
                final EconomyResult refundResult = this.economyGateway.deposit(playerId, refund);
                if (!refundResult.success()) {
                    final BigDecimal chargedUnitPrice = this.effectiveUnitPrice(quote.totalPrice(), amountBought);

                    LOGGER.log(
                        Level.SEVERE,
                        "Failed to automatically refund player "
                            + playerId
                            + " after a partially delivered purchase of "
                            + amountBought
                            + "/"
                            + amount
                            + "x "
                            + itemKey.value()
                            + ". Charged="
                            + quote.totalPrice()
                            + ", intended refund="
                            + refund
                            + ", reason="
                            + refundResult.message()
                    );

                    this.transactionLogService.logPurchase(
                        playerId,
                        itemKey,
                        amountBought,
                        chargedUnitPrice,
                        quote.totalPrice()
                    );

                    return new BuyResult(
                        false,
                        itemKey,
                        amountBought,
                        chargedUnitPrice,
                        quote.totalPrice(),
                        RejectionReason.INTERNAL_ERROR,
                        "Bought "
                            + amountBought
                            + "x "
                            + entry.displayName()
                            + this.formatDeliverySuffix(delivery)
                            + ", but the automatic refund for undelivered items failed. Please contact staff."
                    );
                }
            }
        }

        this.transactionLogService.logPurchase(playerId, itemKey, amountBought, quote.unitPrice(), actualTotalPrice);

        final String message;
        if (amountBought == amount) {
            message = "Bought "
                + amountBought
                + "x "
                + entry.displayName()
                + " for "
                + actualTotalPrice
                + this.formatDeliverySuffix(delivery);
        } else {
            message = "Bought "
                + amountBought
                + "x "
                + entry.displayName()
                + " for "
                + actualTotalPrice
                + this.formatDeliverySuffix(delivery)
                + " (refunded "
                + undeliveredAmount
                + " undelivered item(s))";
        }

        return new BuyResult(true, itemKey, amountBought, quote.unitPrice(), actualTotalPrice, null, message);
    }

    private boolean hasAnyEnabledDeliveryTarget() {
        return this.globalConfig.buyToHeldShulkerEnabled()
            || this.globalConfig.buyToLookedAtContainerEnabled()
            || this.globalConfig.buyToInventoryEnabled()
            || this.globalConfig.buyDropAtFeetEnabled();
    }

    private DeliveryOutcome deliverPurchase(final Player player, final Material material, final int amount) {
        int remaining = amount;
        final List<String> deliverySegments = new ArrayList<>(4);

        if (this.globalConfig.buyToHeldShulkerEnabled() && remaining > 0) {
            final DeliveryStepResult step = this.deliverToHeldShulker(player, material, remaining);
            remaining -= step.amountDelivered();
            this.appendDeliverySegment(deliverySegments, step.amountDelivered(), "held shulker");
        }

        if (this.globalConfig.buyToLookedAtContainerEnabled() && remaining > 0) {
            final DeliveryStepResult step = this.deliverToLookedAtContainer(player, material, remaining);
            remaining -= step.amountDelivered();
            this.appendDeliverySegment(deliverySegments, step.amountDelivered(), step.targetLabel());
        }

        if (this.globalConfig.buyToInventoryEnabled() && remaining > 0) {
            final int delivered = this.addAsMuchAsPossible(player.getInventory(), material, remaining);
            remaining -= delivered;
            this.appendDeliverySegment(deliverySegments, delivered, "inventory");
        }

        if (this.globalConfig.buyDropAtFeetEnabled() && remaining > 0) {
            final ItemStack toDrop = new ItemStack(material, remaining);
            player.getWorld().dropItem(player.getLocation(), toDrop);
            this.appendDeliverySegment(deliverySegments, remaining, "dropped at feet");
            remaining = 0;
        }

        return new DeliveryOutcome(amount - remaining, remaining, List.copyOf(deliverySegments));
    }

    private DeliveryStepResult deliverToHeldShulker(final Player player, final Material material, final int amount) {
        final ItemStack held = player.getInventory().getItemInMainHand();
        if (!this.isHeldShulkerItem(held)) {
            return DeliveryStepResult.none();
        }

        final ItemStack updatedHeld = held.clone();
        final BlockStateMeta updatedMeta = (BlockStateMeta) updatedHeld.getItemMeta();
        if (updatedMeta == null || !(updatedMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return DeliveryStepResult.none();
        }

        final int delivered = this.addAsMuchAsPossible(shulkerBox.getInventory(), material, amount);
        if (delivered <= 0) {
            return DeliveryStepResult.none();
        }

        updatedMeta.setBlockState(shulkerBox);
        updatedHeld.setItemMeta(updatedMeta);
        player.getInventory().setItemInMainHand(updatedHeld);
        return new DeliveryStepResult(delivered, "held shulker");
    }

    private DeliveryStepResult deliverToLookedAtContainer(final Player player, final Material material, final int amount) {
        final Block targetBlock = player.getTargetBlockExact(CONTAINER_TARGET_RANGE);
        final SupportedContainerTarget target = this.resolveSupportedBlockTarget(targetBlock);
        if (target == null) {
            return DeliveryStepResult.none();
        }

        if (this.isLocked(target.state())) {
            return DeliveryStepResult.none();
        }

        final ContainerAccessResult accessResult = this.containerAccessService.canAccessPlacedContainer(player, target.block());
        if (!accessResult.allowed()) {
            return DeliveryStepResult.none();
        }

        final int delivered = this.addAsMuchAsPossible(target.inventory(), material, amount);
        if (delivered <= 0) {
            return DeliveryStepResult.none();
        }

        return new DeliveryStepResult(delivered, target.description());
    }

    private int addAsMuchAsPossible(final Inventory inventory, final Material material, final int amount) {
        if (amount <= 0) {
            return 0;
        }

        final ItemStack toAdd = new ItemStack(material, amount);
        final Map<Integer, ItemStack> leftovers = inventory.addItem(toAdd);
        final int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        return Math.max(0, amount - leftoverAmount);
    }

    private SupportedContainerTarget resolveSupportedBlockTarget(final Block targetBlock) {
        if (targetBlock == null) {
            return null;
        }

        final BlockState state = targetBlock.getState();
        final String description = this.describeTargetBlock(targetBlock);
        if (state instanceof Chest chest) {
            return new SupportedContainerTarget(targetBlock, state, chest.getInventory(), description);
        }
        if (state instanceof Barrel barrel) {
            return new SupportedContainerTarget(targetBlock, state, barrel.getInventory(), description);
        }
        if (state instanceof ShulkerBox shulkerBox) {
            return new SupportedContainerTarget(targetBlock, state, shulkerBox.getInventory(), description);
        }
        return null;
    }

    private boolean isHeldShulkerItem(final ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !this.isShulkerBoxItem(stack.getType())) {
            return false;
        }
        return stack.getItemMeta() instanceof BlockStateMeta blockStateMeta
            && blockStateMeta.getBlockState() instanceof ShulkerBox;
    }

    private boolean isShulkerBoxItem(final Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }

    private boolean isLocked(final BlockState state) {
        return state instanceof Lockable lockable && lockable.isLocked();
    }

    private String describeTargetBlock(final Block targetBlock) {
        if (targetBlock == null) {
            return "container";
        }
        return this.friendlyMaterialName(targetBlock.getType()).toLowerCase();
    }

    private String friendlyMaterialName(final Material material) {
        final String[] parts = material.name().toLowerCase().split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private void appendDeliverySegment(final List<String> deliverySegments, final int amountDelivered, final String targetLabel) {
        if (amountDelivered <= 0) {
            return;
        }
        deliverySegments.add(amountDelivered + " to " + targetLabel);
    }

    private String formatDeliverySuffix(final DeliveryOutcome delivery) {
        if (delivery.segments().isEmpty()) {
            return "";
        }
        return " (delivered: " + String.join(", ", delivery.segments()) + ")";
    }

    private BigDecimal effectiveUnitPrice(final BigDecimal totalPrice, final int amount) {
        if (amount <= 0) {
            return ZERO_MONEY;
        }

        return totalPrice.divide(BigDecimal.valueOf(amount), 2, RoundingMode.HALF_UP);
    }

    private record DeliveryOutcome(int amountDelivered, int amountUndelivered, List<String> segments) {
    }

    private record DeliveryStepResult(int amountDelivered, String targetLabel) {
        private static DeliveryStepResult none() {
            return new DeliveryStepResult(0, "");
        }
    }

    private record SupportedContainerTarget(Block block, BlockState state, Inventory inventory, String description) {
    }
}

```

---

## File: `src/main/resources/config.yml`

```yaml
turnover:
  interval-ticks: 72000

gui:
  page-size: 45
  title-root: "Shop"
  title-browse-prefix: "Shop - "
  title-detail-prefix: "Buy - "

commands:
  base-command: shop
  admin-command: shopadmin

logging:
  debug: false

buy-delivery:
  use-held-shulker: false
  use-looked-at-container: false
  use-player-inventory: true
  drop-at-feet: false

```

---

