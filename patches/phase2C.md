# wild_economy — Commit 2C Copy-Ready Files

## Status

This document contains **copy-ready contents** for **Commit 2C**.

Commit 2C scope:

* stock snapshot/state logic
* sell pricing logic
* transaction logging
* sell service orchestration
* `/shop sellhand`
* `/shop sellall`
* minimal command routing for working sell commands

This is the first commit that should make the Exchange meaningfully usable.

---

## Important scope note

This commit intentionally focuses only on the **sell path**.

What it does **not** do yet:

* buy flow
* buy GUI
* turnover scheduler execution
* MySQL repository implementation
* rich message framework

The goal here is a clean first vertical slice:

* validate item
* quote sell value
* remove item(s)
* pay player
* add stock
* log transaction

---

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockStateResolver.java`

```java
package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.domain.StockState;

public final class StockStateResolver {

    public StockState resolve(final long stockCount, final long stockCap) {
        if (stockCap <= 0L) {
            return stockCount <= 0L ? StockState.OUT_OF_STOCK : StockState.HEALTHY;
        }
        if (stockCount <= 0L) {
            return StockState.OUT_OF_STOCK;
        }

        final double fillRatio = (double) stockCount / (double) stockCap;
        if (fillRatio >= 1.0D) {
            return StockState.SATURATED;
        }
        if (fillRatio >= 0.75D) {
            return StockState.HIGH;
        }
        if (fillRatio >= 0.25D) {
            return StockState.HEALTHY;
        }
        return StockState.LOW;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/stock/StockServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.stock;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.StockState;
import com.splatage.wild_economy.exchange.repository.ExchangeStockRepository;
import java.util.Objects;

public final class StockServiceImpl implements StockService {

    private final ExchangeStockRepository stockRepository;
    private final ExchangeCatalog exchangeCatalog;
    private final StockStateResolver stockStateResolver;

    public StockServiceImpl(
        final ExchangeStockRepository stockRepository,
        final ExchangeCatalog exchangeCatalog,
        final StockStateResolver stockStateResolver
    ) {
        this.stockRepository = Objects.requireNonNull(stockRepository, "stockRepository");
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockStateResolver = Objects.requireNonNull(stockStateResolver, "stockStateResolver");
    }

    @Override
    public StockSnapshot getSnapshot(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final long stockCount = this.stockRepository.getStock(itemKey);
        final long stockCap = Math.max(0L, entry.stockCap());
        final double fillRatio = stockCap <= 0L ? 0.0D : Math.min(1.0D, (double) stockCount / (double) stockCap);
        final StockState stockState = this.stockStateResolver.resolve(stockCount, stockCap);

        return new StockSnapshot(itemKey, stockCount, stockCap, fillRatio, stockState);
    }

    @Override
    public long getAvailableRoom(final ItemKey itemKey) {
        final StockSnapshot snapshot = this.getSnapshot(itemKey);
        if (snapshot.stockCap() <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, snapshot.stockCap() - snapshot.stockCount());
    }

    @Override
    public void addStock(final ItemKey itemKey, final int amount) {
        if (amount <= 0) {
            return;
        }
        this.stockRepository.incrementStock(itemKey, amount);
    }

    @Override
    public void removeStock(final ItemKey itemKey, final int amount) {
        if (amount <= 0) {
            return;
        }
        this.stockRepository.decrementStock(itemKey, amount);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.pricing;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public final class PricingServiceImpl implements PricingService {

    private final ExchangeCatalog exchangeCatalog;

    public PricingServiceImpl(final ExchangeCatalog exchangeCatalog) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
    }

    @Override
    public BuyQuote quoteBuy(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final BigDecimal unitPrice = this.nonNullPrice(entry.buyPrice());
        final BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
        return new BuyQuote(itemKey, amount, unitPrice, totalPrice);
    }

    @Override
    public SellQuote quoteSell(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final BigDecimal baseUnitPrice = this.nonNullPrice(entry.sellPrice());
        final BigDecimal multiplier = this.resolveSellMultiplier(entry.sellPriceBands(), stockSnapshot.fillRatio());
        final BigDecimal effectiveUnitPrice = baseUnitPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        final BigDecimal totalPrice = effectiveUnitPrice.multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
        final boolean tapered = effectiveUnitPrice.compareTo(baseUnitPrice) < 0;

        return new SellQuote(
            itemKey,
            amount,
            baseUnitPrice,
            effectiveUnitPrice,
            totalPrice,
            stockSnapshot.fillRatio(),
            tapered
        );
    }

    private BigDecimal resolveSellMultiplier(final List<SellPriceBand> bands, final double fillRatio) {
        if (bands == null || bands.isEmpty()) {
            return BigDecimal.ONE;
        }
        for (final SellPriceBand band : bands) {
            if (fillRatio >= band.minFillRatioInclusive() && fillRatio < band.maxFillRatioExclusive()) {
                return band.multiplier();
            }
        }
        return BigDecimal.ONE;
    }

    private BigDecimal nonNullPrice(final BigDecimal price) {
        return price == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : price.setScale(2, RoundingMode.HALF_UP);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/TransactionLogService.java`

Replace the earlier empty interface with this version.

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;

public interface TransactionLogService {
    void logSale(UUID playerId, ItemKey itemKey, int amount, BigDecimal unitPrice, BigDecimal totalValue);
    void logPurchase(UUID playerId, ItemKey itemKey, int amount, BigDecimal unitPrice, BigDecimal totalValue);
    void logTurnover(ItemKey itemKey, int amount);
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/TransactionLogServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.TransactionType;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TransactionLogServiceImpl implements TransactionLogService {

    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    private final ExchangeTransactionRepository transactionRepository;

    public TransactionLogServiceImpl(final ExchangeTransactionRepository transactionRepository) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
    }

    @Override
    public void logSale(
        final UUID playerId,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal unitPrice,
        final BigDecimal totalValue
    ) {
        this.transactionRepository.insert(
            TransactionType.SELL,
            playerId,
            itemKey.value(),
            amount,
            unitPrice,
            totalValue,
            Instant.now(),
            null
        );
    }

    @Override
    public void logPurchase(
        final UUID playerId,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal unitPrice,
        final BigDecimal totalValue
    ) {
        this.transactionRepository.insert(
            TransactionType.BUY,
            playerId,
            itemKey.value(),
            amount,
            unitPrice,
            totalValue,
            Instant.now(),
            null
        );
    }

    @Override
    public void logTurnover(final ItemKey itemKey, final int amount) {
        this.transactionRepository.insert(
            TransactionType.TURNOVER,
            SYSTEM_UUID,
            itemKey.value(),
            amount,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Instant.now(),
            null
        );
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellService.java`

Replace the earlier empty interface with this version.

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.UUID;

public interface ExchangeSellService {
    SellHandResult sellHand(UUID playerId);
    SellAllResult sellAll(UUID playerId);
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.EconomyResult;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellLineResult;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.item.ItemValidationService;
import com.splatage.wild_economy.exchange.item.ValidationResult;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ExchangeSellServiceImpl implements ExchangeSellService {

    private final ExchangeCatalog exchangeCatalog;
    private final ItemValidationService itemValidationService;
    private final StockService stockService;
    private final PricingService pricingService;
    private final EconomyGateway economyGateway;
    private final TransactionLogService transactionLogService;

    public ExchangeSellServiceImpl(
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
    public SellHandResult sellHand(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, "Player is not online");
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        final ValidationResult validation = this.itemValidationService.validateForSell(held);
        if (!validation.valid()) {
            return new SellHandResult(false, null, validation.rejectionReason(), validation.detail());
        }

        final ItemKey itemKey = validation.itemKey();
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final int amount = held.getAmount();
        final long availableRoom = this.stockService.getAvailableRoom(itemKey);
        if (availableRoom < amount) {
            return new SellHandResult(false, null, RejectionReason.STOCK_FULL, "Not enough stock room for this stack");
        }

        final StockSnapshot stockSnapshot = this.stockService.getSnapshot(itemKey);
        final SellQuote quote = this.pricingService.quoteSell(itemKey, amount, stockSnapshot);
        if (quote.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return new SellHandResult(false, null, RejectionReason.SELL_NOT_ALLOWED, "Sell value is zero");
        }

        player.getInventory().setItemInMainHand(null);
        final EconomyResult payout = this.economyGateway.deposit(playerId, quote.totalPrice());
        if (!payout.success()) {
            player.getInventory().setItemInMainHand(held);
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, payout.message());
        }

        this.stockService.addStock(itemKey, amount);
        this.transactionLogService.logSale(playerId, itemKey, amount, quote.effectiveUnitPrice(), quote.totalPrice());

        final SellLineResult lineResult = new SellLineResult(
            itemKey,
            entry.displayName(),
            amount,
            quote.effectiveUnitPrice(),
            quote.totalPrice(),
            quote.tapered()
        );

        final String message = "Sold " + amount + "x " + entry.displayName() + " for " + quote.totalPrice();
        return new SellHandResult(true, lineResult, null, message);
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        final Inventory inventory = player.getInventory();
        final List<SellLineResult> soldLines = new ArrayList<>();
        final List<String> skippedDescriptions = new ArrayList<>();
        BigDecimal totalEarned = BigDecimal.ZERO;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack == null) {
                continue;
            }

            final ValidationResult validation = this.itemValidationService.validateForSell(stack);
            if (!validation.valid()) {
                continue;
            }

            final ItemKey itemKey = validation.itemKey();
            final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
                .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

            final int amount = stack.getAmount();
            final long availableRoom = this.stockService.getAvailableRoom(itemKey);
            if (availableRoom < amount) {
                skippedDescriptions.add(entry.displayName() + " x" + amount + " (stock full)");
                continue;
            }

            final StockSnapshot stockSnapshot = this.stockService.getSnapshot(itemKey);
            final SellQuote quote = this.pricingService.quoteSell(itemKey, amount, stockSnapshot);
            if (quote.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
                skippedDescriptions.add(entry.displayName() + " x" + amount + " (zero value)");
                continue;
            }

            inventory.setItem(slot, null);
            final EconomyResult payout = this.economyGateway.deposit(playerId, quote.totalPrice());
            if (!payout.success()) {
                inventory.setItem(slot, stack);
                skippedDescriptions.add(entry.displayName() + " x" + amount + " (payout failed)");
                continue;
            }

            this.stockService.addStock(itemKey, amount);
            this.transactionLogService.logSale(playerId, itemKey, amount, quote.effectiveUnitPrice(), quote.totalPrice());

            soldLines.add(new SellLineResult(
                itemKey,
                entry.displayName(),
                amount,
                quote.effectiveUnitPrice(),
                quote.totalPrice(),
                quote.tapered()
            ));
            totalEarned = totalEarned.add(quote.totalPrice());
        }

        final boolean success = !soldLines.isEmpty();
        final String message = success
            ? "Sold items for a total of " + totalEarned
            : "No sellable items found";

        return new SellAllResult(
            success,
            List.copyOf(soldLines),
            totalEarned,
            List.copyOf(skippedDescriptions),
            message
        );
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ExchangeServiceImpl implements ExchangeService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;
    private final ExchangeSellService exchangeSellService;

    public ExchangeServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService,
        final ExchangeSellService exchangeSellService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.exchangeSellService = Objects.requireNonNull(exchangeSellService, "exchangeSellService");
    }

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        return this.exchangeSellService.sellHand(playerId);
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        return this.exchangeSellService.sellAll(playerId);
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Buy path not implemented yet");
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(final ItemCategory category, final int page, final int pageSize) {
        return this.exchangeCatalog.byCategory(category).stream()
            .skip((long) Math.max(0, page) * Math.max(1, pageSize))
            .limit(Math.max(1, pageSize))
            .map(this::toCatalogView)
            .collect(Collectors.toList());
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));
        final var snapshot = this.stockService.getSnapshot(itemKey);

        return new ExchangeItemView(
            itemKey,
            entry.displayName(),
            entry.buyPrice(),
            snapshot.stockCount(),
            snapshot.stockCap(),
            snapshot.stockState(),
            entry.buyEnabled()
        );
    }

    private ExchangeCatalogView toCatalogView(final ExchangeCatalogEntry entry) {
        final var snapshot = this.stockService.getSnapshot(entry.itemKey());
        return new ExchangeCatalogView(
            entry.itemKey(),
            entry.displayName(),
            entry.buyPrice(),
            snapshot.stockCount(),
            snapshot.stockState()
        );
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellHandSubcommand.java`

Replace the empty stub with this version.

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellHandSubcommand {

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
}
```

---

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellAllSubcommand.java`

Replace the empty stub with this version.

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellAllSubcommand {

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
        if (!result.skippedDescriptions().isEmpty()) {
            player.sendMessage("Skipped: " + String.join(", ", result.skippedDescriptions()));
        }
        return true;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`

Replace the earlier stub with this version.

```java
package com.splatage.wild_economy.command;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopCommand implements CommandExecutor {

    private final ShopSellHandSubcommand sellHandSubcommand;
    private final ShopSellAllSubcommand sellAllSubcommand;

    public ShopCommand(
        final ShopSellHandSubcommand sellHandSubcommand,
        final ShopSellAllSubcommand sellAllSubcommand
    ) {
        this.sellHandSubcommand = Objects.requireNonNull(sellHandSubcommand, "sellHandSubcommand");
        this.sellAllSubcommand = Objects.requireNonNull(sellAllSubcommand, "sellAllSubcommand");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Shop GUI coming soon. Use /shop sellhand or /shop sellall.");
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "sellhand" -> this.sellHandSubcommand.execute(sender);
            case "sellall" -> this.sellAllSubcommand.execute(sender);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /shop sellhand or /shop sellall.");
                yield true;
            }
        };
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

Replace the Commit 2B version with this Commit 2C wiring version.

```java
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
```

---

## Commit 2C behavior notes

### `/shop sellhand`

This should now:

* validate the held item
* reject non-canonical or non-configured items
* reject stock-full stacks
* compute saturation-aware sell value
* remove the item from the hand
* pay the player
* add stock
* log the transaction

### `/shop sellall`

This should now:

* scan the inventory
* validate stack-by-stack
* process each valid stack in order
* let later stacks receive more tapered value if stock fills during the command
* skip stock-full or zero-value stacks
* pay per sold stack immediately via combined running total through repeated deposit calls
* log each sold stack

This is intentionally simple and correctness-first.

---

## Known limitations after Commit 2C

These are expected and acceptable at this stage:

* buy flow still unimplemented
* `/shop` with no args still only prints a placeholder message
* sell messages are simple, not fully message-config backed
* `sellall` deposits per stack, not as a single final deposit
* no partial-stack selling to fill remaining room
* no asynchronous persistence optimization yet

These are fine for the first working vertical slice.

---

## Recommended next artifact

The best next artifact is:

**Commit 2D cleanup/polish** or **Commit 3 buy GUI plan**

If staying on the sell path, Commit 2D should refine:

* message formatting
* single combined payout for `sellall`
* optional reload command
* better skipped-item summaries

If moving forward functionally, Commit 3 should start:

* browse model
* `/shop` GUI root
* buy service
* item detail buy flow
