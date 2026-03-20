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

    private static final int MAX_BUY_AMOUNT = 64;

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

        final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);
        final int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        final int amountBought = Math.max(0, amount - leftoverAmount);

        if (leftoverAmount > 0 && playerStocked) {
            this.stockService.addStock(itemKey, leftoverAmount);
        }

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
