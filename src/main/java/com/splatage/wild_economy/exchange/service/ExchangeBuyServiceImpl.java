package com.splatage.wild_economy.exchange.service;

public final class ExchangeBuyServiceImpl implements ExchangeBuyService {
}
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
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
        final var balance = this.economyGateway.getBalance(playerId);
        if (balance.compareTo(quote.totalPrice()) < 0) {
            return new BuyResult(false, itemKey, 0, quote.unitPrice(), quote.totalPrice(), RejectionReason.INSUFFICIENT_FUNDS, "Not enough money");
        }

        final Material material = Material.matchMaterial(itemKey.value().replace("minecraft:", "").toUpperCase());
        if (material == null || material == Material.AIR) {
            return new BuyResult(false, itemKey, 0, quote.unitPrice(), quote.totalPrice(), RejectionReason.INTERNAL_ERROR, "Invalid material mapping");
        }

        final ItemStack toGive = new ItemStack(material, amount);
        if (player.getInventory().firstEmpty() == -1) {
            return new BuyResult(false, itemKey, 0, quote.unitPrice(), quote.totalPrice(), RejectionReason.INVENTORY_FULL, "Inventory is full");
        }

        final EconomyResult withdrawal = this.economyGateway.withdraw(playerId, quote.totalPrice());
        if (!withdrawal.success()) {
            return new BuyResult(false, itemKey, 0, quote.unitPrice(), quote.totalPrice(), RejectionReason.INTERNAL_ERROR, withdrawal.message());
        }

        player.getInventory().addItem(toGive);
        if (entry.policyMode() == ItemPolicyMode.PLAYER_STOCKED) {
            this.stockService.removeStock(itemKey, amount);
        }
        this.transactionLogService.logPurchase(playerId, itemKey, amount, quote.unitPrice(), quote.totalPrice());

        final String message = "Bought " + amount + "x " + entry.displayName() + " for " + quote.totalPrice();
        return new BuyResult(true, itemKey, amount, quote.unitPrice(), quote.totalPrice(), null, message);
    }
}
