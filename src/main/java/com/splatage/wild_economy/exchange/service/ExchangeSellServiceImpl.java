package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.EconomyResult;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.PlannedSale;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
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
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public final class ExchangeSellServiceImpl implements ExchangeSellService {

    private static final int CONTAINER_TARGET_RANGE = 5;

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

        final SalePlanning planning = this.planSalesFromInventory(
            player.getInventory(),
            true,
            "protected container item; use /sellcontainer"
        );

        if (planning.plannedSales().isEmpty()) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, planning.skippedDescriptions(), "No sellable items found");
        }

        final Inventory inventory = player.getInventory();
        for (final PlannedSale sale : planning.plannedSales()) {
            inventory.setItem(sale.slot(), null);
        }

        final EconomyResult payout = this.economyGateway.deposit(playerId, planning.totalEarned());
        if (!payout.success()) {
            for (final PlannedSale sale : planning.plannedSales()) {
                inventory.setItem(sale.slot(), sale.originalStack());
            }
            return new SellAllResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "Payout failed: " + payout.message()
            );
        }

        final List<SellLineResult> soldLines = this.completePlannedSales(playerId, planning.plannedSales());

        String message = "Sold " + soldLines.size() + " stack(s) for a total of " + planning.totalEarned();
        if (planning.taperedAny()) {
            message += " (some items sold at reduced value due to high stock)";
        }

        return new SellAllResult(
            true,
            List.copyOf(soldLines),
            planning.totalEarned(),
            planning.skippedDescriptions(),
            message
        );
    }

    @Override
    public SellContainerResult sellContainer(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), null, "Player is not online");
        }

        final Block targetBlock = player.getTargetBlockExact(CONTAINER_TARGET_RANGE);
        final SupportedContainerTarget blockTarget = this.resolveSupportedBlockTarget(targetBlock);
        if (blockTarget != null) {
            return this.sellFromInventoryTarget(playerId, blockTarget.inventory(), blockTarget.description());
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        if (this.isHeldShulkerItem(held)) {
            return this.sellFromHeldShulker(playerId, player, held);
        }

        return new SellContainerResult(
            false,
            List.of(),
            BigDecimal.ZERO,
            List.of(),
            null,
            "No supported container found. Look at a chest, barrel, or shulker, or hold a shulker box."
        );
    }

    private SellContainerResult sellFromInventoryTarget(
        final UUID playerId,
        final Inventory inventory,
        final String targetDescription
    ) {
        final SalePlanning planning = this.planSalesFromInventory(
            inventory,
            true,
            "nested container not supported"
        );

        if (planning.plannedSales().isEmpty()) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                targetDescription,
                "No sellable items found in " + targetDescription + "."
            );
        }

        for (final PlannedSale sale : planning.plannedSales()) {
            inventory.setItem(sale.slot(), null);
        }

        final EconomyResult payout = this.economyGateway.deposit(playerId, planning.totalEarned());
        if (!payout.success()) {
            for (final PlannedSale sale : planning.plannedSales()) {
                inventory.setItem(sale.slot(), sale.originalStack());
            }
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                targetDescription,
                "Payout failed: " + payout.message()
            );
        }

        final List<SellLineResult> soldLines = this.completePlannedSales(playerId, planning.plannedSales());
        String message = "Sold contents of " + targetDescription + " for a total of " + planning.totalEarned();
        if (planning.taperedAny()) {
            message += " (some items sold at reduced value due to high stock)";
        }

        return new SellContainerResult(
            true,
            List.copyOf(soldLines),
            planning.totalEarned(),
            planning.skippedDescriptions(),
            targetDescription,
            message
        );
    }

    private SellContainerResult sellFromHeldShulker(
        final UUID playerId,
        final Player player,
        final ItemStack heldShulker
    ) {
        final ItemStack originalHeld = heldShulker.clone();
        final BlockStateMeta blockStateMeta = (BlockStateMeta) heldShulker.getItemMeta();
        if (blockStateMeta == null || !(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                "held shulker box",
                "Held shulker box is not readable."
            );
        }

        final SalePlanning planning = this.planSalesFromInventory(
            shulkerBox.getInventory(),
            true,
            "nested container not supported"
        );

        if (planning.plannedSales().isEmpty()) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "held shulker box",
                "No sellable items found in held shulker box."
            );
        }

        for (final PlannedSale sale : planning.plannedSales()) {
            shulkerBox.getInventory().setItem(sale.slot(), null);
        }

        final ItemStack updatedHeld = heldShulker.clone();
        final BlockStateMeta updatedMeta = (BlockStateMeta) updatedHeld.getItemMeta();
        if (updatedMeta == null) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "held shulker box",
                "Failed to update held shulker box contents."
            );
        }
        updatedMeta.setBlockState(shulkerBox);
        updatedHeld.setItemMeta(updatedMeta);
        player.getInventory().setItemInMainHand(updatedHeld);

        final EconomyResult payout = this.economyGateway.deposit(playerId, planning.totalEarned());
        if (!payout.success()) {
            player.getInventory().setItemInMainHand(originalHeld);
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "held shulker box",
                "Payout failed: " + payout.message()
            );
        }

        final List<SellLineResult> soldLines = this.completePlannedSales(playerId, planning.plannedSales());
        String message = "Sold contents of held shulker box for a total of " + planning.totalEarned();
        if (planning.taperedAny()) {
            message += " (some items sold at reduced value due to high stock)";
        }

        return new SellContainerResult(
            true,
            List.copyOf(soldLines),
            planning.totalEarned(),
            planning.skippedDescriptions(),
            "held shulker box",
            message
        );
    }

    private List<SellLineResult> completePlannedSales(final UUID playerId, final List<PlannedSale> plannedSales) {
        final List<SellLineResult> soldLines = new ArrayList<>(plannedSales.size());
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
        return soldLines;
    }

    private SalePlanning planSalesFromInventory(
        final Inventory inventory,
        final boolean protectShulkers,
        final String protectedShulkerReason
    ) {
        final List<PlannedSale> plannedSales = new ArrayList<>();
        final List<String> skippedDescriptions = new ArrayList<>();
        BigDecimal totalEarned = BigDecimal.ZERO;
        boolean taperedAny = false;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            if (protectShulkers && this.isShulkerBoxItem(stack.getType())) {
                skippedDescriptions.add(this.friendlyMaterialName(stack.getType()) + " x" + stack.getAmount() + " (" + protectedShulkerReason + ")");
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

        return new SalePlanning(
            List.copyOf(plannedSales),
            List.copyOf(skippedDescriptions),
            totalEarned,
            taperedAny
        );
    }

    private SupportedContainerTarget resolveSupportedBlockTarget(final Block targetBlock) {
        if (targetBlock == null) {
            return null;
        }

        final BlockState state = targetBlock.getState();
        if (state instanceof Chest chest) {
            return new SupportedContainerTarget(chest.getInventory(), this.friendlyMaterialName(targetBlock.getType()).toLowerCase());
        }
        if (state instanceof Barrel barrel) {
            return new SupportedContainerTarget(barrel.getInventory(), this.friendlyMaterialName(targetBlock.getType()).toLowerCase());
        }
        if (state instanceof ShulkerBox shulkerBox) {
            return new SupportedContainerTarget(shulkerBox.getInventory(), this.friendlyMaterialName(targetBlock.getType()).toLowerCase());
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

    private String friendlyMaterialName(final Material material) {
        final String[] parts = material.name().toLowerCase().split("_");
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                builder.append(parts[i].substring(1));
            }
        }
        return builder.toString();
    }

    private record SalePlanning(
        List<PlannedSale> plannedSales,
        List<String> skippedDescriptions,
        BigDecimal totalEarned,
        boolean taperedAny
    ) {
    }

    private record SupportedContainerTarget(
        Inventory inventory,
        String description
    ) {
    }
}
