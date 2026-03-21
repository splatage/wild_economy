# wild_economy Phase 3 step 1 (`b71fb98a2cc6d66a991fc1c3fc9c1c39d8207a23`)

This bundle contains complete replacement and new files for the first Phase 3 pass.

Scope of this pass:
- add a read-only admin review GUI on top of the existing Phase 2 backend
- make `/shopadmin` with no args open the admin review GUI for players
- keep command-based admin flows intact
- add root review menu, review bucket browser, rule impact browser, and item inspector GUI
- keep GUI editing out of scope

---

## File: `src/main/java/com/splatage/wild_economy/command/ShopAdminCommand.java`

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogDecisionTrace;
import com.splatage.wild_economy.catalog.admin.AdminCatalogDiffEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogItemKeys;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPhaseOneService;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.gui.admin.AdminMenuRouter;
import com.splatage.wild_economy.catalog.admin.AdminCatalogValidationIssue;
import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopAdminCommand implements CommandExecutor {

    private final WildEconomyPlugin plugin;
    private final AdminCatalogPhaseOneService catalogService;
    private final AdminMenuRouter adminMenuRouter;

    public ShopAdminCommand(final WildEconomyPlugin plugin, final AdminMenuRouter adminMenuRouter) {
        this.plugin = plugin;
        this.catalogService = new AdminCatalogPhaseOneService(plugin);
        this.adminMenuRouter = adminMenuRouter;
    }

    @Override
    public boolean onCommand(
        final CommandSender sender,
        final Command command,
        final String label,
        final String[] args
    ) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                this.adminMenuRouter.openRoot(player);
            } else {
                this.sendUsage(sender);
            }
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "reload" -> this.handleReload(sender);
            case "gui" -> this.handleGui(sender);
            case "generatecatalog" -> this.handleCatalogPreview(sender);
            case "catalog" -> this.handleCatalog(sender, args);
            case "item" -> this.handleItemInspect(sender, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown admin subcommand.");
                this.sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleCatalog(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin catalog <preview|validate|diff|apply>.");
            return true;
        }

        final String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "preview" -> this.handleCatalogPreview(sender);
            case "validate" -> this.handleCatalogValidate(sender);
            case "diff" -> this.handleCatalogDiff(sender);
            case "apply" -> this.handleCatalogApply(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown catalog action.");
                sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin catalog <preview|validate|diff|apply>.");
                yield true;
            }
        };
    }

    private boolean handleGui(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the admin review GUI.");
            return true;
        }
        this.adminMenuRouter.openRoot(player);
        return true;
    }

    private boolean handleReload(final CommandSender sender) {
        try {
            this.plugin.reloadConfig();
            this.plugin.getBootstrap().reload();
            sender.sendMessage(ChatColor.GREEN + "wild_economy reloaded.");
        } catch (final RuntimeException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to reload wild_economy", exception);
            sender.sendMessage(ChatColor.RED + "Reload failed. Check console for details.");
        }
        return true;
    }

    private boolean handleCatalogPreview(final CommandSender sender) {
        return this.runCatalogAction(sender, "preview", false, true);
    }

    private boolean handleCatalogValidate(final CommandSender sender) {
        return this.runCatalogAction(sender, "validate", false, false);
    }

    private boolean handleCatalogDiff(final CommandSender sender) {
        return this.runCatalogAction(sender, "diff", false, false);
    }

    private boolean handleCatalogApply(final CommandSender sender) {
        return this.runCatalogAction(sender, "apply", true, false);
    }


    private boolean handleItemInspect(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin item <item_key>.");
            return true;
        }

        try {
            final AdminCatalogBuildResult result = this.catalogService.build(false);
            final String requestedKey = AdminCatalogItemKeys.canonicalize(args[1]);
            AdminCatalogDecisionTrace trace = null;
            for (final AdminCatalogDecisionTrace candidate : result.decisionTraces()) {
                if (requestedKey.equals(AdminCatalogItemKeys.canonicalize(candidate.itemKey()))) {
                    trace = candidate;
                    break;
                }
            }

            if (trace == null) {
                sender.sendMessage(ChatColor.RED + "No generated catalog decision found for '" + requestedKey + "'.");
                sender.sendMessage(ChatColor.YELLOW + "Run /shopadmin catalog preview and confirm the item key.");
                return true;
            }

            AdminCatalogPlanEntry planEntry = null;
            for (final AdminCatalogPlanEntry candidate : result.proposedEntries()) {
                if (requestedKey.equals(AdminCatalogItemKeys.canonicalize(candidate.itemKey()))) {
                    planEntry = candidate;
                    break;
                }
            }

            final List<String> reviewBuckets = this.findReviewBuckets(trace, planEntry);

            sender.sendMessage(ChatColor.GOLD + "Item inspector: " + trace.displayName() + ChatColor.GRAY + " (" + trace.itemKey() + ")");
            sender.sendMessage(
                ChatColor.YELLOW
                    + "Category "
                    + trace.classifiedCategory().name()
                    + " -> "
                    + trace.finalCategory().name()
                    + ", derivation "
                    + trace.derivationReason().name()
                    + ", depth "
                    + String.valueOf(trace.derivationDepth())
                    + "."
            );
            sender.sendMessage(
                ChatColor.AQUA
                    + "Policy "
                    + trace.baseSuggestedPolicy().name()
                    + " -> "
                    + trace.finalPolicy().name()
                    + ", stock-profile "
                    + trace.stockProfile()
                    + ", eco-envelope "
                    + trace.ecoEnvelope()
                    + "."
            );
            if (planEntry != null) {
                sender.sendMessage(
                    ChatColor.AQUA
                        + "Runtime "
                        + planEntry.runtimePolicy()
                        + ", buy="
                        + planEntry.buyEnabled()
                        + ", sell="
                        + planEntry.sellEnabled()
                        + ", buy-price="
                        + String.valueOf(planEntry.buyPrice())
                        + ", sell-price="
                        + String.valueOf(planEntry.sellPrice())
                        + "."
                );
            }

            final List<String> matchedButLost = new ArrayList<>();
            for (final String matchedRuleId : trace.matchedRuleIds()) {
                if (!matchedRuleId.equals(trace.winningRuleId())) {
                    matchedButLost.add(matchedRuleId);
                }
            }

            sender.sendMessage(
                ChatColor.GRAY
                    + "Winning rule: "
                    + String.valueOf(trace.winningRuleId())
                    + ", matched rules: "
                    + trace.matchedRuleIds()
                    + ", manual override="
                    + trace.manualOverrideApplied()
                    + "."
            );
            if (!matchedButLost.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "Matched but lost: " + matchedButLost + ".");
            }
            if (!reviewBuckets.isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + "Review buckets: " + reviewBuckets + ".");
            }
            if (trace.postRuleAdjustment() != null && !trace.postRuleAdjustment().isBlank()) {
                sender.sendMessage(ChatColor.YELLOW + "Adjustment: " + trace.postRuleAdjustment());
            }
            if (trace.note() != null && !trace.note().isBlank()) {
                sender.sendMessage(ChatColor.GRAY + "Notes: " + trace.note());
            }
            sender.sendMessage(
                ChatColor.GREEN
                    + "Detailed traces are also written to "
                    + result.generatedDirectory().getPath()
                    + "/item-decision-traces.yml."
            );
            return true;
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to inspect generated catalog item", exception);
            sender.sendMessage(ChatColor.RED + "Item inspect failed: " + exception.getMessage());
            return true;
        }
    }

    private boolean runCatalogAction(

        final CommandSender sender,
        final String actionName,
        final boolean apply,
        final boolean includeTopItems
    ) {
        try {
            final AdminCatalogBuildResult result = this.catalogService.build(apply);

            this.sendSummary(sender, result, actionName);
            this.sendValidationSummary(sender, result);
            this.sendPolicySummary(sender, result);
            this.sendReviewSummary(sender, result);
            this.sendDiffSummary(sender, result, includeTopItems);

            if (apply) {
                sender.sendMessage(ChatColor.GREEN + "Published catalog written to " + result.liveCatalogFile().getPath());
                sender.sendMessage(ChatColor.GREEN + "Generated reports written to " + result.generatedDirectory().getPath());
                if (result.snapshotDirectory() != null) {
                    sender.sendMessage(ChatColor.GREEN + "Snapshot created at " + result.snapshotDirectory().getPath());
                }
                sender.sendMessage(ChatColor.YELLOW + "Reloading plugin to apply the new live catalog...");
                this.plugin.reloadConfig();
                this.plugin.getBootstrap().reload();
                sender.sendMessage(ChatColor.GREEN + "wild_economy reloaded with the published catalog.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Generated reports written to " + result.generatedDirectory().getPath());
            }
            sender.sendMessage(
                ChatColor.GREEN
                    + "Additional review reports: generated/generated-rule-impacts.yml and generated/generated-review-buckets.yml."
            );
            return true;
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to " + actionName + " catalog", exception);
            sender.sendMessage(ChatColor.RED + "Catalog " + actionName + " failed: " + exception.getMessage());
            return true;
        }
    }

    private void sendSummary(final CommandSender sender, final AdminCatalogBuildResult result, final String actionName) {
        sender.sendMessage(ChatColor.GOLD + "Catalog " + actionName + " complete.");
        sender.sendMessage(
            ChatColor.YELLOW
                + "Scanned "
                + result.totalScanned()
                + " items, proposed "
                + result.proposedEntries().size()
                + " entries, live-enabled "
                + result.liveEntries().size()
                + " entries."
        );
        sender.sendMessage(
            ChatColor.YELLOW
                + "Disabled "
                + result.disabledCount()
                + ", unresolved "
                + result.unresolvedCount()
                + ", warnings "
                + result.warningCount()
                + ", errors "
                + result.errorCount()
                + "."
        );
    }

    private void sendValidationSummary(final CommandSender sender, final AdminCatalogBuildResult result) {
        if (result.validationIssues().isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Validation passed with no issues.");
            return;
        }

        for (final AdminCatalogValidationIssue issue : result.validationIssues().stream().limit(6).toList()) {
            final ChatColor color = issue.severity() == AdminCatalogValidationIssue.Severity.ERROR
                ? ChatColor.RED
                : ChatColor.YELLOW;
            sender.sendMessage(color + "[" + issue.severity().name() + "] " + issue.message());
        }

        if (result.validationIssues().size() > 6) {
            sender.sendMessage(
                ChatColor.YELLOW
                    + "Additional validation issues were written to generated/generated-validation.yml."
            );
        }
    }

    private void sendPolicySummary(final CommandSender sender, final AdminCatalogBuildResult result) {
        final Map<CatalogPolicy, Integer> counts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            counts.put(policy, 0);
        }
        result.proposedEntries().forEach(entry -> counts.compute(entry.policy(), (ignored, value) -> value + 1));

        sender.sendMessage(
            ChatColor.AQUA
                + "Policies: ALWAYS_AVAILABLE="
                + counts.get(CatalogPolicy.ALWAYS_AVAILABLE)
                + ", EXCHANGE="
                + counts.get(CatalogPolicy.EXCHANGE)
                + ", SELL_ONLY="
                + counts.get(CatalogPolicy.SELL_ONLY)
                + ", DISABLED="
                + counts.get(CatalogPolicy.DISABLED)
        );
    }

    private void sendReviewSummary(final CommandSender sender, final AdminCatalogBuildResult result) {
        int liveMiscCount = 0;
        int noRootPathCount = 0;
        int blockedPathCount = 0;
        int manualOverrideCount = 0;

        for (final AdminCatalogPlanEntry entry : result.proposedEntries()) {
            if (entry.policy() != CatalogPolicy.DISABLED && entry.category() == CatalogCategory.MISC) {
                liveMiscCount++;
            }
        }
        for (final AdminCatalogDecisionTrace trace : result.decisionTraces()) {
            if (trace.derivationReason() == DerivationReason.NO_RECIPE_AND_NO_ROOT) {
                noRootPathCount++;
            }
            if (trace.derivationReason() == DerivationReason.ALL_PATHS_BLOCKED) {
                blockedPathCount++;
            }
            if (trace.manualOverrideApplied()) {
                manualOverrideCount++;
            }
        }

        sender.sendMessage(
            ChatColor.AQUA
                + "Review buckets: live-MISC="
                + liveMiscCount
                + ", no-root-path="
                + noRootPathCount
                + ", blocked-paths="
                + blockedPathCount
                + ", manual-overrides="
                + manualOverrideCount
                + "."
        );
    }

    private void sendDiffSummary(
        final CommandSender sender,
        final AdminCatalogBuildResult result,
        final boolean includeTopItems
    ) {
        if (result.diffEntries().isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No live catalog differences detected.");
            return;
        }

        int added = 0;
        int removed = 0;
        int changed = 0;
        for (final AdminCatalogDiffEntry entry : result.diffEntries()) {
            switch (entry.changeType()) {
                case ADDED -> added++;
                case REMOVED -> removed++;
                case CHANGED -> changed++;
            }
        }

        sender.sendMessage(
            ChatColor.AQUA
                + "Diff: added "
                + added
                + ", removed "
                + removed
                + ", changed "
                + changed
                + "."
        );

        if (includeTopItems) {
            for (final AdminCatalogDiffEntry entry : result.diffEntries().stream().limit(5).toList()) {
                sender.sendMessage(ChatColor.GRAY + "- " + entry.itemKey() + ": " + entry.summary());
            }
        }
    }


    private List<String> findReviewBuckets(
        final AdminCatalogDecisionTrace trace,
        final AdminCatalogPlanEntry planEntry
    ) {
        final List<String> buckets = new ArrayList<>();
        if (planEntry != null && planEntry.policy() != CatalogPolicy.DISABLED && planEntry.category() == CatalogCategory.MISC) {
            buckets.add("live-misc-items");
        }
        if (trace.derivationReason() == DerivationReason.NO_RECIPE_AND_NO_ROOT) {
            buckets.add("no-root-path");
        }
        if (trace.derivationReason() == DerivationReason.ALL_PATHS_BLOCKED) {
            buckets.add("blocked-paths");
        }
        if (trace.manualOverrideApplied()) {
            buckets.add("manual-overrides");
        }
        if (trace.finalPolicy() == CatalogPolicy.SELL_ONLY) {
            buckets.add("sell-only-review");
        }
        return buckets;
    }


    private void sendUsage(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin to open the admin review GUI.");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin gui");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin reload");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin catalog <preview|validate|diff|apply>");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin item <item_key>");
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
import com.splatage.wild_economy.gui.admin.AdminItemInspectorMenu;
import com.splatage.wild_economy.gui.admin.AdminMenuListener;
import com.splatage.wild_economy.gui.admin.AdminMenuRouter;
import com.splatage.wild_economy.gui.admin.AdminReviewBucketMenu;
import com.splatage.wild_economy.gui.admin.AdminRootMenu;
import com.splatage.wild_economy.gui.admin.AdminRuleImpactMenu;
import com.splatage.wild_economy.gui.ExchangeItemDetailMenu;
import com.splatage.wild_economy.gui.ExchangeRootMenu;
import com.splatage.wild_economy.gui.ExchangeSubcategoryMenu;
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
    private AdminMenuRouter adminMenuRouter;
    private AdminMenuListener adminMenuListener;
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
            itemDetailMenu
        );
        this.plugin.getServer().getPluginManager().registerEvents(this.shopMenuListener, this.plugin);

        final AdminRootMenu adminRootMenu = new AdminRootMenu();
        final AdminReviewBucketMenu adminReviewBucketMenu = new AdminReviewBucketMenu();
        final AdminRuleImpactMenu adminRuleImpactMenu = new AdminRuleImpactMenu();
        final AdminItemInspectorMenu adminItemInspectorMenu = new AdminItemInspectorMenu();

        this.adminMenuRouter = new AdminMenuRouter(
            this.plugin,
            this.platformExecutor,
            adminRootMenu,
            adminReviewBucketMenu,
            adminRuleImpactMenu,
            adminItemInspectorMenu
        );

        adminRootMenu.setAdminMenuRouter(this.adminMenuRouter);
        adminReviewBucketMenu.setAdminMenuRouter(this.adminMenuRouter);
        adminRuleImpactMenu.setAdminMenuRouter(this.adminMenuRouter);
        adminItemInspectorMenu.setAdminMenuRouter(this.adminMenuRouter);

        this.adminMenuListener = new AdminMenuListener(
            adminRootMenu,
            adminReviewBucketMenu,
            adminRuleImpactMenu,
            adminItemInspectorMenu
        );
        this.plugin.getServer().getPluginManager().registerEvents(this.adminMenuListener, this.plugin);
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
            shopAdmin.setExecutor(new ShopAdminCommand(this.plugin, this.adminMenuRouter));
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

## File: `src/main/java/com/splatage/wild_economy/gui/admin/AdminCatalogViewState.java`

```java
package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogDecisionTrace;
import com.splatage.wild_economy.catalog.admin.AdminCatalogItemKeys;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogReviewBucket;
import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import java.util.List;
import java.util.Objects;

public record AdminCatalogViewState(
    AdminCatalogBuildResult buildResult,
    List<AdminCatalogRuleImpact> ruleImpacts,
    List<AdminCatalogReviewBucket> reviewBuckets,
    String lastAction
) {
    public AdminCatalogViewState {
        buildResult = Objects.requireNonNull(buildResult, "buildResult");
        ruleImpacts = ruleImpacts == null ? List.of() : List.copyOf(ruleImpacts);
        reviewBuckets = reviewBuckets == null ? List.of() : List.copyOf(reviewBuckets);
        lastAction = lastAction == null ? "preview" : lastAction;
    }

    public AdminCatalogDecisionTrace findTrace(final String itemKey) {
        final String canonical = AdminCatalogItemKeys.canonicalize(itemKey);
        for (final AdminCatalogDecisionTrace trace : this.buildResult.decisionTraces()) {
            if (canonical.equals(AdminCatalogItemKeys.canonicalize(trace.itemKey()))) {
                return trace;
            }
        }
        return null;
    }

    public AdminCatalogPlanEntry findPlanEntry(final String itemKey) {
        final String canonical = AdminCatalogItemKeys.canonicalize(itemKey);
        for (final AdminCatalogPlanEntry entry : this.buildResult.proposedEntries()) {
            if (canonical.equals(AdminCatalogItemKeys.canonicalize(entry.itemKey()))) {
                return entry;
            }
        }
        return null;
    }

    public AdminCatalogRuleImpact findRuleImpact(final String ruleId) {
        if (ruleId == null) {
            return null;
        }
        for (final AdminCatalogRuleImpact ruleImpact : this.ruleImpacts) {
            if (ruleId.equals(ruleImpact.ruleId())) {
                return ruleImpact;
            }
        }
        return null;
    }

    public AdminCatalogReviewBucket findReviewBucket(final String bucketId) {
        if (bucketId == null) {
            return null;
        }
        for (final AdminCatalogReviewBucket reviewBucket : this.reviewBuckets) {
            if (bucketId.equals(reviewBucket.bucketId())) {
                return reviewBucket;
            }
        }
        return null;
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/gui/admin/AdminMenuHolder.java`

```java
package com.splatage.wild_economy.gui.admin;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AdminMenuHolder implements InventoryHolder {

    public enum ViewType {
        ROOT,
        REVIEW_BUCKET_LIST,
        REVIEW_BUCKET_DETAIL,
        RULE_IMPACT_LIST,
        RULE_IMPACT_DETAIL,
        ITEM_INSPECTOR
    }

    private final AdminCatalogViewState state;
    private final ViewType viewType;
    private final String bucketId;
    private final String ruleId;
    private final String itemKey;
    private final String returnBucketId;
    private final String returnRuleId;
    private Inventory inventory;

    private AdminMenuHolder(
        final AdminCatalogViewState state,
        final ViewType viewType,
        final String bucketId,
        final String ruleId,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.viewType = Objects.requireNonNull(viewType, "viewType");
        this.bucketId = bucketId;
        this.ruleId = ruleId;
        this.itemKey = itemKey;
        this.returnBucketId = returnBucketId;
        this.returnRuleId = returnRuleId;
    }

    public static AdminMenuHolder root(final AdminCatalogViewState state) {
        return new AdminMenuHolder(state, ViewType.ROOT, null, null, null, null, null);
    }

    public static AdminMenuHolder reviewBucketList(final AdminCatalogViewState state) {
        return new AdminMenuHolder(state, ViewType.REVIEW_BUCKET_LIST, null, null, null, null, null);
    }

    public static AdminMenuHolder reviewBucketDetail(final AdminCatalogViewState state, final String bucketId) {
        return new AdminMenuHolder(state, ViewType.REVIEW_BUCKET_DETAIL, bucketId, null, null, null, null);
    }

    public static AdminMenuHolder ruleImpactList(final AdminCatalogViewState state) {
        return new AdminMenuHolder(state, ViewType.RULE_IMPACT_LIST, null, null, null, null, null);
    }

    public static AdminMenuHolder ruleImpactDetail(final AdminCatalogViewState state, final String ruleId) {
        return new AdminMenuHolder(state, ViewType.RULE_IMPACT_DETAIL, null, ruleId, null, null, null);
    }

    public static AdminMenuHolder itemInspector(
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId
    ) {
        return new AdminMenuHolder(state, ViewType.ITEM_INSPECTOR, null, null, itemKey, returnBucketId, returnRuleId);
    }

    public Inventory createInventory(final int size, final String title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        return this.inventory;
    }

    @Override
    public Inventory getInventory() {
        if (this.inventory == null) {
            throw new IllegalStateException("Inventory has not been created for this holder yet");
        }
        return this.inventory;
    }

    public AdminCatalogViewState state() {
        return this.state;
    }

    public ViewType viewType() {
        return this.viewType;
    }

    public String bucketId() {
        return this.bucketId;
    }

    public String ruleId() {
        return this.ruleId;
    }

    public String itemKey() {
        return this.itemKey;
    }

    public String returnBucketId() {
        return this.returnBucketId;
    }

    public String returnRuleId() {
        return this.returnRuleId;
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/gui/admin/AdminMenuRouter.java`

```java
package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPhaseOneService;
import com.splatage.wild_economy.catalog.admin.AdminCatalogReviewBucket;
import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AdminMenuRouter {

    private final WildEconomyPlugin plugin;
    private final PlatformExecutor platformExecutor;
    private final AdminCatalogPhaseOneService catalogService;
    private final AdminRootMenu adminRootMenu;
    private final AdminReviewBucketMenu adminReviewBucketMenu;
    private final AdminRuleImpactMenu adminRuleImpactMenu;
    private final AdminItemInspectorMenu adminItemInspectorMenu;

    public AdminMenuRouter(
        final WildEconomyPlugin plugin,
        final PlatformExecutor platformExecutor,
        final AdminRootMenu adminRootMenu,
        final AdminReviewBucketMenu adminReviewBucketMenu,
        final AdminRuleImpactMenu adminRuleImpactMenu,
        final AdminItemInspectorMenu adminItemInspectorMenu
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.catalogService = new AdminCatalogPhaseOneService(plugin);
        this.adminRootMenu = Objects.requireNonNull(adminRootMenu, "adminRootMenu");
        this.adminReviewBucketMenu = Objects.requireNonNull(adminReviewBucketMenu, "adminReviewBucketMenu");
        this.adminRuleImpactMenu = Objects.requireNonNull(adminRuleImpactMenu, "adminRuleImpactMenu");
        this.adminItemInspectorMenu = Objects.requireNonNull(adminItemInspectorMenu, "adminItemInspectorMenu");
    }

    public void openRoot(final Player player) {
        this.rebuildAndOpenRoot(player, "preview", false);
    }

    public void openRoot(final Player player, final AdminCatalogViewState state) {
        this.platformExecutor.runOnPlayer(player, () -> this.adminRootMenu.open(player, state));
    }

    public void rebuildAndOpenRoot(final Player player, final String actionName, final boolean apply) {
        try {
            final AdminCatalogViewState state = this.buildState(apply, actionName);
            if (apply) {
                this.sendApplyMessages(player, state);
                this.platformExecutor.runOnPlayer(player, player::closeInventory);
                this.plugin.reloadConfig();
                this.plugin.getBootstrap().reload();
                player.sendMessage(ChatColor.GREEN + "wild_economy reloaded with the published catalog.");
                return;
            }
            this.sendActionSummary(player, state, actionName);
            this.platformExecutor.runOnPlayer(player, () -> this.adminRootMenu.open(player, state));
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to " + actionName + " catalog from admin GUI", exception);
            player.sendMessage(ChatColor.RED + "Catalog " + actionName + " failed: " + exception.getMessage());
        }
    }

    public void openReviewBucketList(final Player player, final AdminCatalogViewState state) {
        this.platformExecutor.runOnPlayer(player, () -> this.adminReviewBucketMenu.openList(player, state));
    }

    public void openReviewBucketDetail(final Player player, final AdminCatalogViewState state, final String bucketId) {
        this.platformExecutor.runOnPlayer(player, () -> this.adminReviewBucketMenu.openDetail(player, state, bucketId));
    }

    public void openRuleImpactList(final Player player, final AdminCatalogViewState state) {
        this.platformExecutor.runOnPlayer(player, () -> this.adminRuleImpactMenu.openList(player, state));
    }

    public void openRuleImpactDetail(final Player player, final AdminCatalogViewState state, final String ruleId) {
        this.platformExecutor.runOnPlayer(player, () -> this.adminRuleImpactMenu.openDetail(player, state, ruleId));
    }

    public void openItemInspector(
        final Player player,
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId
    ) {
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.adminItemInspectorMenu.open(player, state, itemKey, returnBucketId, returnRuleId)
        );
    }

    public void goBack(final Player player) {
        final AdminMenuHolder holder = this.currentHolder(player);
        if (holder == null) {
            this.openRoot(player);
            return;
        }

        switch (holder.viewType()) {
            case ROOT -> this.openRoot(player);
            case REVIEW_BUCKET_LIST -> this.openRoot(player, holder.state());
            case REVIEW_BUCKET_DETAIL -> this.openReviewBucketList(player, holder.state());
            case RULE_IMPACT_LIST -> this.openRoot(player, holder.state());
            case RULE_IMPACT_DETAIL -> this.openRuleImpactList(player, holder.state());
            case ITEM_INSPECTOR -> {
                if (holder.returnBucketId() != null) {
                    this.openReviewBucketDetail(player, holder.state(), holder.returnBucketId());
                } else if (holder.returnRuleId() != null) {
                    this.openRuleImpactDetail(player, holder.state(), holder.returnRuleId());
                } else {
                    this.openRoot(player, holder.state());
                }
            }
        }
    }

    public void closeAllAdminViews() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (this.currentHolder(player) == null) {
                continue;
            }
            this.platformExecutor.runOnPlayer(player, player::closeInventory);
        }
    }

    public static AdminMenuHolder getAdminMenuHolder(final Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        final InventoryHolder holder = inventory.getHolder();
        if (holder instanceof AdminMenuHolder adminMenuHolder) {
            return adminMenuHolder;
        }
        return null;
    }

    private AdminMenuHolder currentHolder(final Player player) {
        return getAdminMenuHolder(player.getOpenInventory().getTopInventory());
    }

    private AdminCatalogViewState buildState(final boolean apply, final String actionName) throws IOException {
        final AdminCatalogBuildResult buildResult = this.catalogService.build(apply);
        final File generatedDirectory = buildResult.generatedDirectory();
        final List<AdminCatalogRuleImpact> ruleImpacts = this.loadRuleImpacts(
            new File(generatedDirectory, "generated-rule-impacts.yml")
        );
        final List<AdminCatalogReviewBucket> reviewBuckets = this.loadReviewBuckets(
            new File(generatedDirectory, "generated-review-buckets.yml")
        );
        return new AdminCatalogViewState(buildResult, ruleImpacts, reviewBuckets, actionName);
    }

    private List<AdminCatalogRuleImpact> loadRuleImpacts(final File file) {
        if (!file.isFile()) {
            return List.of();
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection rulesSection = yaml.getConfigurationSection("rules");
        if (rulesSection == null) {
            return List.of();
        }
        final List<AdminCatalogRuleImpact> ruleImpacts = new ArrayList<>();
        for (final String ruleId : rulesSection.getKeys(false)) {
            final ConfigurationSection section = rulesSection.getConfigurationSection(ruleId);
            if (section == null) {
                continue;
            }
            ruleImpacts.add(new AdminCatalogRuleImpact(
                ruleId,
                section.getBoolean("fallback-rule"),
                section.getBoolean("has-match-criteria"),
                section.getInt("match-count"),
                section.getInt("win-count"),
                section.getInt("loss-count"),
                this.loadPolicyCounts(section.getConfigurationSection("winning-policies")),
                this.loadPolicyCounts(section.getConfigurationSection("lost-to-policies")),
                this.loadStringCounts(section.getConfigurationSection("lost-to-rules")),
                section.getStringList("sample-matched-items"),
                section.getStringList("sample-winning-items"),
                section.getStringList("sample-lost-items")
            ));
        }
        return ruleImpacts;
    }

    private List<AdminCatalogReviewBucket> loadReviewBuckets(final File file) {
        if (!file.isFile()) {
            return List.of();
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection bucketsSection = yaml.getConfigurationSection("buckets");
        if (bucketsSection == null) {
            return List.of();
        }
        final List<AdminCatalogReviewBucket> reviewBuckets = new ArrayList<>();
        for (final String bucketId : bucketsSection.getKeys(false)) {
            final ConfigurationSection section = bucketsSection.getConfigurationSection(bucketId);
            if (section == null) {
                continue;
            }
            reviewBuckets.add(new AdminCatalogReviewBucket(
                bucketId,
                section.getString("description", ""),
                section.getInt("count"),
                section.getStringList("sample-items"),
                this.loadStringCounts(section.getConfigurationSection("subgroup-counts")),
                this.loadSampleMap(section.getConfigurationSection("subgroup-sample-items"))
            ));
        }
        reviewBuckets.sort(Comparator.comparingInt(AdminCatalogReviewBucket::count).reversed());
        return reviewBuckets;
    }

    private Map<CatalogPolicy, Integer> loadPolicyCounts(final ConfigurationSection section) {
        final Map<CatalogPolicy, Integer> counts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            counts.put(policy, Integer.valueOf(0));
        }
        if (section == null) {
            return counts;
        }
        for (final String key : section.getKeys(false)) {
            try {
                counts.put(CatalogPolicy.valueOf(key.toUpperCase(Locale.ROOT)), Integer.valueOf(section.getInt(key)));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        return counts;
    }

    private Map<String, Integer> loadStringCounts(final ConfigurationSection section) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        if (section == null) {
            return counts;
        }
        for (final String key : section.getKeys(false)) {
            counts.put(key, Integer.valueOf(section.getInt(key)));
        }
        return counts;
    }

    private Map<String, List<String>> loadSampleMap(final ConfigurationSection section) {
        final Map<String, List<String>> sampleMap = new LinkedHashMap<>();
        if (section == null) {
            return sampleMap;
        }
        for (final String key : section.getKeys(false)) {
            sampleMap.put(key, List.copyOf(section.getStringList(key)));
        }
        return sampleMap;
    }

    private void sendActionSummary(final Player player, final AdminCatalogViewState state, final String actionName) {
        final AdminCatalogBuildResult result = state.buildResult();
        player.sendMessage(
            ChatColor.GOLD + "Catalog " + actionName + " complete: scanned "
                + result.totalScanned()
                + ", proposed "
                + result.proposedEntries().size()
                + ", live-enabled "
                + result.liveEntries().size()
                + "."
        );
        player.sendMessage(
            ChatColor.YELLOW + "Warnings "
                + result.warningCount()
                + ", errors "
                + result.errorCount()
                + ". Reports written to "
                + result.generatedDirectory().getPath()
                + "."
        );
    }

    private void sendApplyMessages(final Player player, final AdminCatalogViewState state) {
        final AdminCatalogBuildResult result = state.buildResult();
        player.sendMessage(ChatColor.GREEN + "Published catalog written to " + result.liveCatalogFile().getPath());
        player.sendMessage(ChatColor.GREEN + "Generated reports written to " + result.generatedDirectory().getPath());
        if (result.snapshotDirectory() != null) {
            player.sendMessage(ChatColor.GREEN + "Snapshot created at " + result.snapshotDirectory().getPath());
        }
        player.sendMessage(ChatColor.YELLOW + "Reloading plugin to apply the new live catalog...");
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/gui/admin/AdminMenuListener.java`

```java
package com.splatage.wild_economy.gui.admin;

import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class AdminMenuListener implements Listener {

    private final AdminRootMenu adminRootMenu;
    private final AdminReviewBucketMenu adminReviewBucketMenu;
    private final AdminRuleImpactMenu adminRuleImpactMenu;
    private final AdminItemInspectorMenu adminItemInspectorMenu;

    public AdminMenuListener(
        final AdminRootMenu adminRootMenu,
        final AdminReviewBucketMenu adminReviewBucketMenu,
        final AdminRuleImpactMenu adminRuleImpactMenu,
        final AdminItemInspectorMenu adminItemInspectorMenu
    ) {
        this.adminRootMenu = Objects.requireNonNull(adminRootMenu, "adminRootMenu");
        this.adminReviewBucketMenu = Objects.requireNonNull(adminReviewBucketMenu, "adminReviewBucketMenu");
        this.adminRuleImpactMenu = Objects.requireNonNull(adminRuleImpactMenu, "adminRuleImpactMenu");
        this.adminItemInspectorMenu = Objects.requireNonNull(adminItemInspectorMenu, "adminItemInspectorMenu");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final AdminMenuHolder holder = AdminMenuRouter.getAdminMenuHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        event.setCancelled(true);

        switch (holder.viewType()) {
            case ROOT -> this.adminRootMenu.handleClick(event, holder);
            case REVIEW_BUCKET_LIST, REVIEW_BUCKET_DETAIL -> this.adminReviewBucketMenu.handleClick(event, holder);
            case RULE_IMPACT_LIST, RULE_IMPACT_DETAIL -> this.adminRuleImpactMenu.handleClick(event, holder);
            case ITEM_INSPECTOR -> this.adminItemInspectorMenu.handleClick(event, holder);
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        final AdminMenuHolder holder = AdminMenuRouter.getAdminMenuHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        final int topSize = event.getView().getTopInventory().getSize();
        for (final int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/gui/admin/AdminRootMenu.java`

```java
package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminRootMenu {

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void open(final Player player, final AdminCatalogViewState state) {
        final AdminMenuHolder holder = AdminMenuHolder.root(state);
        final Inventory inventory = holder.createInventory(45, "Shop Admin");

        inventory.setItem(10, this.summaryItem(state.buildResult(), state.lastAction()));
        inventory.setItem(12, this.policyItem(state.buildResult()));
        inventory.setItem(14, this.reviewItem(state));

        inventory.setItem(28, this.actionButton(Material.LIME_STAINED_GLASS_PANE, "Preview"));
        inventory.setItem(29, this.actionButton(Material.YELLOW_STAINED_GLASS_PANE, "Validate"));
        inventory.setItem(30, this.actionButton(Material.PAPER, "Diff"));
        inventory.setItem(32, this.actionButton(Material.EMERALD_BLOCK, "Apply"));

        inventory.setItem(34, this.actionButton(Material.CHEST, "Review Buckets"));
        inventory.setItem(35, this.actionButton(Material.COMPARATOR, "Rule Impacts"));
        inventory.setItem(40, this.actionButton(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 28 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false);
            case 29 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "validate", false);
            case 30 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "diff", false);
            case 32 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "apply", true);
            case 34 -> this.adminMenuRouter.openReviewBucketList(player, holder.state());
            case 35 -> this.adminMenuRouter.openRuleImpactList(player, holder.state());
            case 40 -> player.closeInventory();
            default -> {
            }
        }
    }

    private ItemStack summaryItem(final AdminCatalogBuildResult result, final String lastAction) {
        return this.item(
            Material.BOOK,
            "Catalog Summary",
            List.of(
                "Last action: " + lastAction,
                "Scanned: " + result.totalScanned(),
                "Proposed: " + result.proposedEntries().size(),
                "Live-enabled: " + result.liveEntries().size(),
                "Disabled: " + result.disabledCount(),
                "Unresolved: " + result.unresolvedCount(),
                "Warnings: " + result.warningCount(),
                "Errors: " + result.errorCount()
            )
        );
    }

    private ItemStack policyItem(final AdminCatalogBuildResult result) {
        final Map<CatalogPolicy, Integer> counts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            counts.put(policy, Integer.valueOf(0));
        }
        for (final AdminCatalogPlanEntry entry : result.proposedEntries()) {
            counts.compute(entry.policy(), (ignored, value) -> Integer.valueOf(value.intValue() + 1));
        }
        return this.item(
            Material.WRITABLE_BOOK,
            "Policy Split",
            List.of(
                "ALWAYS_AVAILABLE: " + counts.get(CatalogPolicy.ALWAYS_AVAILABLE),
                "EXCHANGE: " + counts.get(CatalogPolicy.EXCHANGE),
                "SELL_ONLY: " + counts.get(CatalogPolicy.SELL_ONLY),
                "DISABLED: " + counts.get(CatalogPolicy.DISABLED)
            )
        );
    }

    private ItemStack reviewItem(final AdminCatalogViewState state) {
        return this.item(
            Material.ENDER_CHEST,
            "Review Data",
            List.of(
                "Review buckets: " + state.reviewBuckets().size(),
                "Rule impacts: " + state.ruleImpacts().size(),
                "Use the buttons below to browse",
                "generated/generated-review-buckets.yml",
                "generated/generated-rule-impacts.yml"
            )
        );
    }

    private ItemStack actionButton(final Material material, final String name) {
        return this.item(material, name, List.of());
    }

    private ItemStack item(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/gui/admin/AdminReviewBucketMenu.java`

```java
package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogReviewBucket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminReviewBucketMenu {

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void openList(final Player player, final AdminCatalogViewState state) {
        final AdminMenuHolder holder = AdminMenuHolder.reviewBucketList(state);
        final Inventory inventory = holder.createInventory(54, "Admin - Review Buckets");
        final List<AdminCatalogReviewBucket> buckets = this.sortedBuckets(state);

        int slot = 0;
        for (final AdminCatalogReviewBucket bucket : buckets) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.bucketItem(bucket));
            slot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.button(Material.BOOK, "Refresh Root"));

        player.openInventory(inventory);
    }

    public void openDetail(final Player player, final AdminCatalogViewState state, final String bucketId) {
        final AdminCatalogReviewBucket bucket = state.findReviewBucket(bucketId);
        if (bucket == null) {
            player.sendMessage("Unknown review bucket: " + bucketId);
            this.openList(player, state);
            return;
        }

        final AdminMenuHolder holder = AdminMenuHolder.reviewBucketDetail(state, bucketId);
        final Inventory inventory = holder.createInventory(54, "Bucket - " + this.displayBucketId(bucket.bucketId()));

        inventory.setItem(4, this.bucketSummaryItem(bucket));

        int subgroupSlot = 9;
        for (final Map.Entry<String, Integer> entry : bucket.subgroupCounts().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(9)
            .toList()) {
            inventory.setItem(subgroupSlot, this.subgroupItem(entry.getKey(), entry.getValue(), bucket.subgroupSampleItems()));
            subgroupSlot++;
        }

        int sampleSlot = 18;
        for (final String itemKey : bucket.sampleItems()) {
            if (sampleSlot >= 45) {
                break;
            }
            inventory.setItem(sampleSlot, this.itemButton(itemKey, "Open inspector"));
            sampleSlot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.CHEST, "Bucket List"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (holder.viewType()) {
            case REVIEW_BUCKET_LIST -> this.handleListClick(event, player, holder.state());
            case REVIEW_BUCKET_DETAIL -> this.handleDetailClick(event, player, holder);
            default -> {
            }
        }
    }

    private void handleListClick(final InventoryClickEvent event, final Player player, final AdminCatalogViewState state) {
        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final List<AdminCatalogReviewBucket> buckets = this.sortedBuckets(state);
            if (slot < buckets.size()) {
                this.adminMenuRouter.openReviewBucketDetail(player, state, buckets.get(slot).bucketId());
            }
            return;
        }

        switch (slot) {
            case 45 -> this.adminMenuRouter.openRoot(player, state);
            case 49 -> player.closeInventory();
            case 53 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false);
            default -> {
            }
        }
    }

    private void handleDetailClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final AdminCatalogReviewBucket bucket = holder.state().findReviewBucket(holder.bucketId());
        if (bucket == null) {
            this.adminMenuRouter.openReviewBucketList(player, holder.state());
            return;
        }

        if (slot >= 18 && slot < 45) {
            final int index = slot - 18;
            if (index < bucket.sampleItems().size()) {
                this.adminMenuRouter.openItemInspector(
                    player,
                    holder.state(),
                    bucket.sampleItems().get(index),
                    bucket.bucketId(),
                    null
                );
            }
            return;
        }

        switch (slot) {
            case 45 -> this.adminMenuRouter.openReviewBucketList(player, holder.state());
            case 49 -> this.adminMenuRouter.openReviewBucketList(player, holder.state());
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private List<AdminCatalogReviewBucket> sortedBuckets(final AdminCatalogViewState state) {
        final List<AdminCatalogReviewBucket> buckets = new ArrayList<>(state.reviewBuckets());
        buckets.sort(Comparator.comparingInt(AdminCatalogReviewBucket::count).reversed());
        return buckets;
    }

    private ItemStack bucketItem(final AdminCatalogReviewBucket bucket) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Count: " + bucket.count());
        for (final Map.Entry<String, Integer> entry : bucket.subgroupCounts().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(4)
            .toList()) {
            lore.add(entry.getKey() + ": " + entry.getValue());
        }
        lore.add("Click to open bucket detail.");
        return this.item(Material.CHEST, this.displayBucketId(bucket.bucketId()), lore);
    }

    private ItemStack bucketSummaryItem(final AdminCatalogReviewBucket bucket) {
        final List<String> lore = new ArrayList<>();
        lore.add(bucket.description());
        lore.add("Total items: " + bucket.count());
        lore.add("Sample items below open the inspector.");
        if (!bucket.subgroupCounts().isEmpty()) {
            lore.add("Top subgroup: " + bucket.subgroupCounts().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .orElse("n/a"));
        }
        return this.item(Material.ENDER_CHEST, this.displayBucketId(bucket.bucketId()), lore);
    }

    private ItemStack subgroupItem(
        final String subgroupId,
        final int count,
        final Map<String, List<String>> subgroupSampleItems
    ) {
        final List<String> lore = new ArrayList<>();
        lore.add("Count: " + count);
        final List<String> sample = subgroupSampleItems.getOrDefault(subgroupId, List.of());
        for (final String itemKey : sample.stream().limit(5).toList()) {
            lore.add(itemKey);
        }
        return this.item(Material.PAPER, subgroupId, lore);
    }

    private ItemStack itemButton(final String itemKey, final String footer) {
        final Material material = this.resolveMaterial(itemKey);
        return this.item(
            material == null ? Material.BARRIER : material,
            this.displayItemKey(itemKey),
            List.of(itemKey, footer)
        );
    }

    private ItemStack button(final Material material, final String name) {
        return this.item(material, name, List.of());
    }

    private ItemStack item(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material resolveMaterial(final String itemKey) {
        return Material.matchMaterial(itemKey.replace("minecraft:", "").toUpperCase(Locale.ROOT));
    }

    private String displayItemKey(final String itemKey) {
        return itemKey.replace("minecraft:", "").replace('_', ' ');
    }

    private String displayBucketId(final String bucketId) {
        return bucketId.replace('-', ' ');
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/gui/admin/AdminRuleImpactMenu.java`

```java
package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminRuleImpactMenu {

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void openList(final Player player, final AdminCatalogViewState state) {
        final AdminMenuHolder holder = AdminMenuHolder.ruleImpactList(state);
        final Inventory inventory = holder.createInventory(54, "Admin - Rule Impacts");
        final List<AdminCatalogRuleImpact> ruleImpacts = this.sortedRuleImpacts(state);

        int slot = 0;
        for (final AdminCatalogRuleImpact ruleImpact : ruleImpacts) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.ruleItem(ruleImpact));
            slot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.button(Material.BOOK, "Refresh Root"));

        player.openInventory(inventory);
    }

    public void openDetail(final Player player, final AdminCatalogViewState state, final String ruleId) {
        final AdminCatalogRuleImpact ruleImpact = state.findRuleImpact(ruleId);
        if (ruleImpact == null) {
            player.sendMessage("Unknown rule impact: " + ruleId);
            this.openList(player, state);
            return;
        }

        final AdminMenuHolder holder = AdminMenuHolder.ruleImpactDetail(state, ruleId);
        final Inventory inventory = holder.createInventory(54, "Rule - " + ruleImpact.ruleId());

        inventory.setItem(4, this.ruleSummaryItem(ruleImpact));
        inventory.setItem(10, this.policyMapItem("Winning policies", ruleImpact.winningPolicies()));
        inventory.setItem(12, this.lossRuleItem(ruleImpact));
        inventory.setItem(14, this.policyMapItem("Lost to policies", ruleImpact.lostToPolicies()));

        int winSlot = 27;
        for (final String itemKey : ruleImpact.sampleWinningItems()) {
            if (winSlot >= 36) {
                break;
            }
            inventory.setItem(winSlot, this.itemButton(itemKey, "Winning sample"));
            winSlot++;
        }

        int lostSlot = 36;
        for (final String itemKey : ruleImpact.sampleLostItems()) {
            if (lostSlot >= 45) {
                break;
            }
            inventory.setItem(lostSlot, this.itemButton(itemKey, "Lost sample"));
            lostSlot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.COMPARATOR, "Rule List"));
        inventory.setItem(53, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (holder.viewType()) {
            case RULE_IMPACT_LIST -> this.handleListClick(event, player, holder.state());
            case RULE_IMPACT_DETAIL -> this.handleDetailClick(event, player, holder);
            default -> {
            }
        }
    }

    private void handleListClick(final InventoryClickEvent event, final Player player, final AdminCatalogViewState state) {
        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final List<AdminCatalogRuleImpact> ruleImpacts = this.sortedRuleImpacts(state);
            if (slot < ruleImpacts.size()) {
                this.adminMenuRouter.openRuleImpactDetail(player, state, ruleImpacts.get(slot).ruleId());
            }
            return;
        }
        switch (slot) {
            case 45 -> this.adminMenuRouter.openRoot(player, state);
            case 49 -> player.closeInventory();
            case 53 -> this.adminMenuRouter.rebuildAndOpenRoot(player, "preview", false);
            default -> {
            }
        }
    }

    private void handleDetailClick(final InventoryClickEvent event, final Player player, final AdminMenuHolder holder) {
        final int slot = event.getRawSlot();
        final AdminCatalogRuleImpact ruleImpact = holder.state().findRuleImpact(holder.ruleId());
        if (ruleImpact == null) {
            this.adminMenuRouter.openRuleImpactList(player, holder.state());
            return;
        }

        if (slot >= 27 && slot < 36) {
            final int index = slot - 27;
            if (index < ruleImpact.sampleWinningItems().size()) {
                this.adminMenuRouter.openItemInspector(
                    player,
                    holder.state(),
                    ruleImpact.sampleWinningItems().get(index),
                    null,
                    ruleImpact.ruleId()
                );
            }
            return;
        }

        if (slot >= 36 && slot < 45) {
            final int index = slot - 36;
            if (index < ruleImpact.sampleLostItems().size()) {
                this.adminMenuRouter.openItemInspector(
                    player,
                    holder.state(),
                    ruleImpact.sampleLostItems().get(index),
                    null,
                    ruleImpact.ruleId()
                );
            }
            return;
        }

        switch (slot) {
            case 45 -> this.adminMenuRouter.openRuleImpactList(player, holder.state());
            case 49 -> this.adminMenuRouter.openRuleImpactList(player, holder.state());
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private List<AdminCatalogRuleImpact> sortedRuleImpacts(final AdminCatalogViewState state) {
        final List<AdminCatalogRuleImpact> ruleImpacts = new ArrayList<>(state.ruleImpacts());
        ruleImpacts.sort(
            Comparator.comparingInt(AdminCatalogRuleImpact::winCount)
                .thenComparingInt(AdminCatalogRuleImpact::matchCount)
                .reversed()
        );
        return ruleImpacts;
    }

    private ItemStack ruleItem(final AdminCatalogRuleImpact ruleImpact) {
        final List<String> lore = new ArrayList<>();
        lore.add("Fallback: " + ruleImpact.fallbackRule());
        lore.add("Matches: " + ruleImpact.matchCount());
        lore.add("Wins: " + ruleImpact.winCount());
        lore.add("Losses: " + ruleImpact.lossCount());
        lore.add("Click to open rule detail.");
        return this.item(Material.COMPARATOR, ruleImpact.ruleId(), lore);
    }

    private ItemStack ruleSummaryItem(final AdminCatalogRuleImpact ruleImpact) {
        final List<String> lore = new ArrayList<>();
        lore.add("Fallback rule: " + ruleImpact.fallbackRule());
        lore.add("Has match criteria: " + ruleImpact.hasMatchCriteria());
        lore.add("Match count: " + ruleImpact.matchCount());
        lore.add("Win count: " + ruleImpact.winCount());
        lore.add("Loss count: " + ruleImpact.lossCount());
        lore.add("Winning and losing sample items below");
        return this.item(Material.REPEATER, ruleImpact.ruleId(), lore);
    }

    private ItemStack lossRuleItem(final AdminCatalogRuleImpact ruleImpact) {
        final List<String> lore = new ArrayList<>();
        if (ruleImpact.lostToRules().isEmpty()) {
            lore.add("No losing-rule breakdown recorded.");
        } else {
            for (final Map.Entry<String, Integer> entry : ruleImpact.lostToRules().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .toList()) {
                lore.add(entry.getKey() + ": " + entry.getValue());
            }
        }
        return this.item(Material.REDSTONE_TORCH, "Lost to rules", lore);
    }

    private ItemStack policyMapItem(final String title, final Map<CatalogPolicy, Integer> counts) {
        final List<String> lore = new ArrayList<>();
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            lore.add(policy.name() + ": " + counts.getOrDefault(policy, Integer.valueOf(0)));
        }
        return this.item(Material.PAPER, title, lore);
    }

    private ItemStack itemButton(final String itemKey, final String footer) {
        final Material material = this.resolveMaterial(itemKey);
        return this.item(
            material == null ? Material.BARRIER : material,
            this.displayItemKey(itemKey),
            List.of(itemKey, footer)
        );
    }

    private ItemStack button(final Material material, final String name) {
        return this.item(material, name, List.of());
    }

    private ItemStack item(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material resolveMaterial(final String itemKey) {
        return Material.matchMaterial(itemKey.replace("minecraft:", "").toUpperCase(Locale.ROOT));
    }

    private String displayItemKey(final String itemKey) {
        return itemKey.replace("minecraft:", "").replace('_', ' ');
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/gui/admin/AdminItemInspectorMenu.java`

```java
package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.catalog.admin.AdminCatalogDecisionTrace;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPlanEntry;
import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminItemInspectorMenu {

    private AdminMenuRouter adminMenuRouter;

    public void setAdminMenuRouter(final AdminMenuRouter adminMenuRouter) {
        this.adminMenuRouter = Objects.requireNonNull(adminMenuRouter, "adminMenuRouter");
    }

    public void open(
        final Player player,
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId
    ) {
        final AdminCatalogDecisionTrace trace = state.findTrace(itemKey);
        if (trace == null) {
            player.sendMessage("No generated catalog decision found for '" + itemKey + "'.");
            this.adminMenuRouter.openRoot(player);
            return;
        }
        final AdminCatalogPlanEntry planEntry = state.findPlanEntry(itemKey);
        final AdminMenuHolder holder = AdminMenuHolder.itemInspector(state, trace.itemKey(), returnBucketId, returnRuleId);
        final Inventory inventory = holder.createInventory(45, "Inspect - " + trace.displayName());

        inventory.setItem(13, this.itemIcon(trace, planEntry));
        inventory.setItem(20, this.decisionItem(trace));
        inventory.setItem(22, this.runtimeItem(planEntry));
        inventory.setItem(24, this.ruleItem(trace, state));
        inventory.setItem(31, this.reviewBucketItem(trace, planEntry));

        inventory.setItem(36, this.button(Material.ARROW, "Back"));
        inventory.setItem(40, this.button(Material.COMPASS, "Root"));
        inventory.setItem(44, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final AdminMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 36 -> this.adminMenuRouter.goBack(player);
            case 40 -> this.adminMenuRouter.openRoot(player, holder.state());
            case 44 -> player.closeInventory();
            default -> {
            }
        }
    }

    private ItemStack itemIcon(final AdminCatalogDecisionTrace trace, final AdminCatalogPlanEntry planEntry) {
        final Material material = this.resolveMaterial(trace.itemKey());
        final List<String> lore = new ArrayList<>();
        lore.add(trace.itemKey());
        lore.add("Final policy: " + trace.finalPolicy().name());
        lore.add("Final category: " + trace.finalCategory().name());
        if (planEntry != null) {
            lore.add("Buy: " + planEntry.buyEnabled() + " @ " + String.valueOf(planEntry.buyPrice()));
            lore.add("Sell: " + planEntry.sellEnabled() + " @ " + String.valueOf(planEntry.sellPrice()));
        }
        return this.item(
            material == null ? Material.BARRIER : material,
            trace.displayName(),
            lore
        );
    }

    private ItemStack decisionItem(final AdminCatalogDecisionTrace trace) {
        final List<String> lore = new ArrayList<>();
        lore.add("Classified: " + trace.classifiedCategory().name());
        lore.add("Final: " + trace.finalCategory().name());
        lore.add("Derivation: " + trace.derivationReason().name());
        lore.add("Depth: " + String.valueOf(trace.derivationDepth()));
        lore.add("Base policy: " + trace.baseSuggestedPolicy().name());
        lore.add("Final policy: " + trace.finalPolicy().name());
        if (trace.postRuleAdjustment() != null && !trace.postRuleAdjustment().isBlank()) {
            lore.add("Adjustment: " + trace.postRuleAdjustment());
        }
        return this.item(Material.BOOK, "Decision", lore);
    }

    private ItemStack runtimeItem(final AdminCatalogPlanEntry planEntry) {
        final List<String> lore = new ArrayList<>();
        if (planEntry == null) {
            lore.add("No runtime plan entry recorded.");
        } else {
            lore.add("Runtime policy: " + planEntry.runtimePolicy());
            lore.add("Buy enabled: " + planEntry.buyEnabled());
            lore.add("Sell enabled: " + planEntry.sellEnabled());
            lore.add("Stock profile: " + planEntry.stockProfile());
            lore.add("Eco envelope: " + planEntry.ecoEnvelope());
            lore.add("Anchor: " + String.valueOf(planEntry.anchorValue()));
            lore.add("Buy price: " + String.valueOf(planEntry.buyPrice()));
            lore.add("Sell price: " + String.valueOf(planEntry.sellPrice()));
        }
        return this.item(Material.WRITABLE_BOOK, "Runtime", lore);
    }

    private ItemStack ruleItem(final AdminCatalogDecisionTrace trace, final AdminCatalogViewState state) {
        final List<String> lore = new ArrayList<>();
        lore.add("Winning rule: " + String.valueOf(trace.winningRuleId()));
        lore.add("Manual override: " + trace.manualOverrideApplied());
        lore.add("Matched rules: " + trace.matchedRuleIds());
        final List<String> matchedButLost = new ArrayList<>();
        for (final String matchedRuleId : trace.matchedRuleIds()) {
            if (!matchedRuleId.equals(trace.winningRuleId())) {
                matchedButLost.add(matchedRuleId);
            }
        }
        if (!matchedButLost.isEmpty()) {
            lore.add("Matched but lost: " + matchedButLost);
        }
        final AdminCatalogRuleImpact winningImpact = state.findRuleImpact(trace.winningRuleId());
        if (winningImpact != null) {
            lore.add("Rule wins: " + winningImpact.winCount());
            lore.add("Rule losses: " + winningImpact.lossCount());
        }
        return this.item(Material.COMPARATOR, "Rules", lore);
    }

    private ItemStack reviewBucketItem(final AdminCatalogDecisionTrace trace, final AdminCatalogPlanEntry planEntry) {
        final List<String> lore = new ArrayList<>();
        final List<String> bucketIds = this.findReviewBuckets(trace, planEntry);
        if (bucketIds.isEmpty()) {
            lore.add("No current review bucket membership.");
        } else {
            lore.addAll(bucketIds);
        }
        return this.item(Material.CHEST, "Review buckets", lore);
    }

    private List<String> findReviewBuckets(
        final AdminCatalogDecisionTrace trace,
        final AdminCatalogPlanEntry planEntry
    ) {
        final List<String> buckets = new ArrayList<>();
        if (planEntry != null && planEntry.policy() != CatalogPolicy.DISABLED && planEntry.category() == CatalogCategory.MISC) {
            buckets.add("live-misc-items");
        }
        if (trace.derivationReason() == DerivationReason.NO_RECIPE_AND_NO_ROOT) {
            buckets.add("no-root-path");
        }
        if (trace.derivationReason() == DerivationReason.ALL_PATHS_BLOCKED) {
            buckets.add("blocked-paths");
        }
        if (trace.manualOverrideApplied()) {
            buckets.add("manual-overrides");
        }
        if (trace.finalPolicy() == CatalogPolicy.SELL_ONLY) {
            buckets.add("sell-only-review");
        }
        return buckets;
    }

    private ItemStack button(final Material material, final String name) {
        return this.item(material, name, List.of());
    }

    private ItemStack item(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material resolveMaterial(final String itemKey) {
        return Material.matchMaterial(itemKey.replace("minecraft:", "").toUpperCase(Locale.ROOT));
    }
}

```

