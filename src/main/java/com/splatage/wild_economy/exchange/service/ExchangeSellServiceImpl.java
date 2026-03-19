package com.splatage.wild_economy.exchange.service;

public final class ExchangeSellServiceImpl implements ExchangeSellService {
}
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
