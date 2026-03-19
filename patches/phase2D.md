# wild_economy — Commit 2D Copy-Ready Files (Sell Path Polish)

## Status

This document contains **copy-ready contents** for **Commit 2D**.

Commit 2D scope:

* polish and harden the sell path built in Commit 2C
* move `/shop sellall` from per-stack deposits to a **single combined payout**
* improve skipped-item and tapered-item summaries
* add a minimal reload admin path
* make command wiring and results cleaner without adding buy GUI yet

This is still part of the **sell-path-first** phase.

---

## Goals of Commit 2D

### Correctness improvements

* `sellall` should compute all valid sales first, then perform **one combined deposit**
* if payout fails, sold items should be restored to inventory
* stock should only increment after successful payout
* transaction logging should reflect committed sales only

### UX improvements

* show clearer `sellall` summary
* explicitly tell the player when some items sold at reduced value due to saturation
* keep `/shop` root response clean while buy GUI is still pending

### Small admin utility

* `/shopadmin reload` should reload config/catalog and rewire runtime state cleanly enough for development/testing

---

# 1. New helper record for staged sell-all planning

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/PlannedSale.java`

```java
package com.splatage.wild_economy.exchange.domain;

import org.bukkit.inventory.ItemStack;

public record PlannedSale(
    int slot,
    ItemStack originalStack,
    ItemKey itemKey,
    String displayName,
    int amount,
    SellQuote quote
) {}
```

---

# 2. Update `ExchangeSellServiceImpl.java`

Replace the Commit 2C version with this Commit 2D version.

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.EconomyResult;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.PlannedSale;
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

        final ItemStack restoreStack = held.clone();
        player.getInventory().setItemInMainHand(null);
        final EconomyResult payout = this.economyGateway.deposit(playerId, quote.totalPrice());
        if (!payout.success()) {
            player.getInventory().setItemInMainHand(restoreStack);
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

        final String message = quote.tapered()
            ? "Sold " + amount + "x " + entry.displayName() + " for " + quote.totalPrice() + " (reduced due to high stock)"
            : "Sold " + amount + "x " + entry.displayName() + " for " + quote.totalPrice();
        return new SellHandResult(true, lineResult, null, message);
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        final Inventory inventory = player.getInventory();
        final List<PlannedSale> plannedSales = new ArrayList<>();
        final List<String> skippedDescriptions = new ArrayList<>();
        BigDecimal totalEarned = BigDecimal.ZERO;
        boolean taperedAny = false;

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

            plannedSales.add(new PlannedSale(
                slot,
                stack.clone(),
                itemKey,
                entry.displayName(),
                amount,
                quote
            ));
            totalEarned = totalEarned.add(quote.totalPrice());
            taperedAny |= quote.tapered();
        }

        if (plannedSales.isEmpty()) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.copyOf(skippedDescriptions), "No sellable items found");
        }

        for (final PlannedSale sale : plannedSales) {
            inventory.setItem(sale.slot(), null);
        }

        final EconomyResult payout = this.economyGateway.deposit(playerId, totalEarned);
        if (!payout.success()) {
            for (final PlannedSale sale : plannedSales) {
                inventory.setItem(sale.slot(), sale.originalStack());
            }
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.copyOf(skippedDescriptions), "Payout failed: " + payout.message());
        }

        final List<SellLineResult> soldLines = new ArrayList<>();
        for (final PlannedSale sale : plannedSales) {
            this.stockService.addStock(sale.itemKey(), sale.amount());
            this.transactionLogService.logSale(
                playerId,
                sale.itemKey(),
                sale.amount(),
                sale.quote().effectiveUnitPrice(),
                sale.quote().totalPrice()
            );
            soldLines.add(new SellLineResult(
                sale.itemKey(),
                sale.displayName(),
                sale.amount(),
                sale.quote().effectiveUnitPrice(),
                sale.quote().totalPrice(),
                sale.quote().tapered()
            ));
        }

        String message = "Sold " + soldLines.size() + " stack(s) for a total of " + totalEarned;
        if (taperedAny) {
            message += " (some items sold at reduced value due to high stock)";
        }

        return new SellAllResult(
            true,
            List.copyOf(soldLines),
            totalEarned,
            List.copyOf(skippedDescriptions),
            message
        );
    }
}
```

---

# 3. Update `ShopSellAllSubcommand.java`

Replace the Commit 2C version with this cleaner summary version.

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellAllSubcommand.java`

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
                player.sendMessage(" - ... and " + (result.skippedDescriptions().size() - maxSkipped) + " more skipped item(s)");
            }
        }

        return true;
    }
}
```

---

# 4. Update `ShopSellHandSubcommand.java`

This adds a cleaner reduced-value indicator.

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellHandSubcommand.java`

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

# 5. Add minimal reload support to `ShopAdminCommand.java`

Replace the earlier stub with this lightweight version.

## File: `src/main/java/com/splatage/wild_economy/command/ShopAdminCommand.java`

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.WildEconomyPlugin;
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
            sender.sendMessage("Use /shopadmin reload");
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            this.plugin.reloadConfig();
            sender.sendMessage("wild_economy config.yml reloaded. Full runtime reload wiring still pending.");
            return true;
        }

        sender.sendMessage("Unknown admin subcommand.");
        return true;
    }
}
```

---

# 6. Update `ShopCommand.java`

This keeps the root response cleaner until GUI browse arrives.

## File: `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`

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
            sender.sendMessage("Shop buy GUI is coming soon. Current commands: /shop sellhand, /shop sellall");
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

# 7. Update `ServiceRegistry.java`

Only small changes are needed here for the admin command constructor.

## File: `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

Replace only the `registerCommands()` method with this version, leaving the rest of the Commit 2C file intact.

```java
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
```

---

# 8. Commit 2D acceptance criteria

After applying these changes:

## `sellhand`

* still sells correctly
* clearly indicates reduced value when stock saturation applies
* restores item to hand if payout fails

## `sellall`

* calculates a full sale plan first
* removes items only after planning succeeds
* performs one combined payout
* restores removed items if payout fails
* increments stock only after successful payout
* writes transaction rows only after successful payout
* returns a clearer player summary

## command surface

* `/shop` without args stays clean and informative
* `/shopadmin reload` exists as a minimal development utility

---

# 9. Known limitations after Commit 2D

Still intentionally deferred:

* actual runtime full reload of catalog/services, not just `config.yml`
* buy flow
* browse GUI
* async DB optimization
* MySQL runtime path
* partial-stack selling into remaining room

These remain acceptable to defer.

---

# 10. Best next step after Commit 2D

The strongest next step is now **Commit 3: buy/browse path**.

That should include:

* `ExchangeBrowseService`
* buy validation and quoting
* `ExchangeBuyServiceImpl`
* `ExchangeRootMenu`
* `ExchangeBrowseMenu`
* `ExchangeItemDetailMenu`
* `/shop` opening the root GUI

At that point the plugin starts feeling like a real Exchange product rather than just a sell backend.
