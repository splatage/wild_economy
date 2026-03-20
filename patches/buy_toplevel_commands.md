# wild_economy buy fix + top-level sell commands patch

Scope locked by user:

* Fix `buy()` inventory-fit behavior.
* Promote `sellhand()` and `sellall()` to authoritative top-level commands.
* Keep soft stock-cap behavior unchanged.
* Dropped transaction logs under pressure remain acceptable.

This patch exports complete files for the affected paths.

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ExchangeBuyServiceImpl implements ExchangeBuyService {

    private final ExchangeCatalog exchangeCatalog;
    private final ItemValidationService itemValidationService;
    private final StockService stockService;
    private final PricingService pricingService;
    private final EconomyGateway economyGateway;
    private final TransactionLogService transactionLogService;

    public ExchangeBuyServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final ItemValidationService itemValidationService,
        final StockService stockService,
        final PricingService pricingService,
        final EconomyGateway economyGateway,
        final TransactionLogService transactionLogService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.itemValidationService = Objects.requireNonNull(itemValidationService, "itemValidationService");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.economyGateway = Objects.requireNonNull(economyGateway, "economyGateway");
        this.transactionLogService = Objects.requireNonNull(transactionLogService, "transactionLogService");
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new BuyResult(false, itemKey, 0, null, null, RejectionReason.INTERNAL_ERROR, "Player is not online");
        }

        if (amount <= 0) {
            return new BuyResult(false, itemKey, 0, null, null, RejectionReason.BUY_NOT_ALLOWED, "Amount must be positive");
        }

        final ValidationResult validation = this.itemValidationService.validateForBuy(itemKey);
        if (!validation.valid()) {
            return new BuyResult(false, itemKey, 0, null, null, validation.rejectionReason(), validation.detail());
        }

        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final StockSnapshot snapshot = this.stockService.getSnapshot(itemKey);
        if (entry.policyMode() == ItemPolicyMode.PLAYER_STOCKED && snapshot.stockCount() < amount) {
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

        final ItemStack toGive = new ItemStack(material, amount);
        if (!this.canFit(player.getInventory(), toGive)) {
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                quote.totalPrice(),
                RejectionReason.INVENTORY_FULL,
                "Not enough inventory space"
            );
        }

        final EconomyResult withdrawal = this.economyGateway.withdraw(playerId, quote.totalPrice());
        if (!withdrawal.success()) {
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

        final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);
        final int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        final int amountBought = Math.max(0, amount - leftoverAmount);

        if (amountBought <= 0) {
            this.economyGateway.deposit(playerId, quote.totalPrice());
            return new BuyResult(
                false,
                itemKey,
                0,
                quote.unitPrice(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                RejectionReason.INVENTORY_FULL,
                "Not enough inventory space"
            );
        }

        final BigDecimal actualTotalPrice = quote.unitPrice()
            .multiply(BigDecimal.valueOf(amountBought))
            .setScale(2, RoundingMode.HALF_UP);

        if (leftoverAmount > 0) {
            final BigDecimal refund = quote.totalPrice().subtract(actualTotalPrice).setScale(2, RoundingMode.HALF_UP);
            if (refund.compareTo(BigDecimal.ZERO) > 0) {
                this.economyGateway.deposit(playerId, refund);
            }
        }

        if (entry.policyMode() == ItemPolicyMode.PLAYER_STOCKED) {
            this.stockService.removeStock(itemKey, amountBought);
        }

        this.transactionLogService.logPurchase(playerId, itemKey, amountBought, quote.unitPrice(), actualTotalPrice);

        final String message = amountBought == amount
            ? "Bought " + amountBought + "x " + entry.displayName() + " for " + actualTotalPrice
            : "Bought " + amountBought + "x " + entry.displayName() + " for " + actualTotalPrice
                + " (inventory accepted fewer than requested)";

        return new BuyResult(true, itemKey, amountBought, quote.unitPrice(), actualTotalPrice, null, message);
    }

    private boolean canFit(final Inventory inventory, final ItemStack itemStack) {
        int remaining = itemStack.getAmount();
        final int slotMax = Math.min(itemStack.getMaxStackSize(), inventory.getMaxStackSize());

        for (final ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType() == Material.AIR) {
                remaining -= slotMax;
                if (remaining <= 0) {
                    return true;
                }
                continue;
            }

            if (!existing.isSimilar(itemStack)) {
                continue;
            }

            final int existingSlotMax = Math.min(existing.getMaxStackSize(), slotMax);
            final int freeSpace = Math.max(0, existingSlotMax - existing.getAmount());
            remaining -= freeSpace;
            if (remaining <= 0) {
                return true;
            }
        }

        return remaining <= 0;
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellHandSubcommand.java`

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellHandSubcommand implements CommandExecutor {

    private final ExchangeService exchangeService;

    public ShopSellHandSubcommand(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        final SellHandResult result = this.exchangeService.sellHand(player.getUniqueId());
        player.sendMessage(result.message());
        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender);
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellAllSubcommand.java`

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellAllSubcommand implements CommandExecutor {

    private final ExchangeService exchangeService;

    public ShopSellAllSubcommand(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        final SellAllResult result = this.exchangeService.sellAll(player.getUniqueId());
        player.sendMessage(result.message());

        if (!result.soldLines().isEmpty()) {
            final int maxLines = Math.min(5, result.soldLines().size());
            for (int i = 0; i < maxLines; i++) {
                final var line = result.soldLines().get(i);
                final String taperSuffix = line.tapered() ? " (reduced)" : "";
                player.sendMessage(" - " + line.amountSold() + "x " + line.displayName() + " for " + line.totalEarned() + taperSuffix);
            }

            if (result.soldLines().size() > maxLines) {
                player.sendMessage(" - ... and " + (result.soldLines().size() - maxLines) + " more stack(s)");
            }
        }

        if (!result.skippedDescriptions().isEmpty()) {
            final int maxSkipped = Math.min(5, result.skippedDescriptions().size());
            player.sendMessage("Skipped:");
            for (int i = 0; i < maxSkipped; i++) {
                player.sendMessage(" - " + result.skippedDescriptions().get(i));
            }

            if (result.skippedDescriptions().size() > maxSkipped) {
                player.sendMessage(" - ... and " + (result.skippedDescriptions().size() - maxSkipped) + " more skipped stack(s)");
            }
        }

        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender);
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`

```java
package com.splatage.wild_economy.command;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopCommand implements CommandExecutor {

    private final ShopOpenSubcommand openSubcommand;
    private final ShopSellHandSubcommand sellHandSubcommand;
    private final ShopSellAllSubcommand sellAllSubcommand;

    public ShopCommand(
        final ShopOpenSubcommand openSubcommand,
        final ShopSellHandSubcommand sellHandSubcommand,
        final ShopSellAllSubcommand sellAllSubcommand
    ) {
        this.openSubcommand = Objects.requireNonNull(openSubcommand, "openSubcommand");
        this.sellHandSubcommand = Objects.requireNonNull(sellHandSubcommand, "sellHandSubcommand");
        this.sellAllSubcommand = Objects.requireNonNull(sellAllSubcommand, "sellAllSubcommand");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            return this.openSubcommand.execute(sender);
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "sellhand" -> this.sellHandSubcommand.execute(sender);
            case "sellall" -> this.sellAllSubcommand.execute(sender);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /shop, /shop sellhand, /shop sellall, /sellhand, or /sellall.");
                yield true;
            }
        };
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

```java
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
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import java.io.File;
import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

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

        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(this.exchangeService);
        final ExchangeSubcategoryMenu subcategoryMenu = new ExchangeSubcategoryMenu(this.exchangeService);
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService);
        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService);

        this.shopMenuRouter = new ShopMenuRouter(rootMenu, subcategoryMenu, browseMenu, itemDetailMenu);

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

        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(
                openSubcommand,
                sellHandSubcommand,
                sellAllSubcommand
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

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand(this.plugin));
        }
    }

    public void registerTasks() {
        this.plugin.getServer().getScheduler().runTaskTimer(
            this.plugin,
            new com.splatage.wild_economy.scheduler.StockTurnoverTask(this.stockTurnoverService),
            this.globalConfig.turnoverIntervalTicks(),
            this.globalConfig.turnoverIntervalTicks()
        );
    }

    public void shutdown() {
        this.plugin.getServer().getScheduler().cancelTasks(this.plugin);

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

## File: `src/main/resources/plugin.yml`

```yaml
name: wild_economy
version: ${version}
main: com.splatage.wild_economy.WildEconomyPlugin
api-version: '1.21'
author: splatage
description: Curated Exchange-first economy plugin for Minecraft

depend:
  - Vault

softdepend:
  - Essentials

commands:
  shop:
    description: Open the shop or use sell commands.
    usage: /shop [sellhand|sellall]
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
  shopadmin:
    description: Admin commands for wild_economy.
    usage: /shopadmin <reload|generatecatalog>
    permission: wild_economy.admin

permissions:
  wild_economy.shop:
    description: Allows use of player shop commands.
    default: true
  wild_economy.shop.sell:
    description: Allows selling to the Exchange.
    default: true
  wild_economy.shop.buy:
    description: Allows buying from the Exchange.
    default: true
  wild_economy.admin:
    description: Allows administrative use of wild_economy.
    default: op
```
