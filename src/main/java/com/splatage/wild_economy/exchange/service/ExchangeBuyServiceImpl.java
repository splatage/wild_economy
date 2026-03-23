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
        return this.buyInternal(playerId, itemKey, amount, null);
    }

    @Override
    public BuyResult buyQuoted(
        final UUID playerId,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal quotedUnitPrice
    ) {
        return this.buyInternal(playerId, itemKey, amount, quotedUnitPrice);
    }

    private BuyResult buyInternal(
        final UUID playerId,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal quotedUnitPrice
    ) {
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

        final BuyQuote quote = this.resolveBuyQuote(itemKey, amount, snapshot, quotedUnitPrice);
        if (quote == null) {
            return new BuyResult(
                false,
                itemKey,
                0,
                null,
                null,
                RejectionReason.INTERNAL_ERROR,
                "Invalid quoted buy price"
            );
        }
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

    private BuyQuote resolveBuyQuote(
        final ItemKey itemKey,
        final int amount,
        final StockSnapshot stockSnapshot,
        final BigDecimal quotedUnitPrice
    ) {
        if (quotedUnitPrice == null) {
            return this.pricingService.quoteBuy(itemKey, amount, stockSnapshot);
        }

        final BigDecimal normalizedQuotedUnitPrice = this.normalizedQuotedUnitPrice(quotedUnitPrice);
        if (normalizedQuotedUnitPrice == null) {
            return null;
        }

        return new BuyQuote(
            itemKey,
            amount,
            normalizedQuotedUnitPrice,
            normalizedQuotedUnitPrice.multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal normalizedQuotedUnitPrice(final BigDecimal quotedUnitPrice) {
        if (quotedUnitPrice == null) {
            return null;
        }

        final BigDecimal normalized = quotedUnitPrice.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }

        return normalized;
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

