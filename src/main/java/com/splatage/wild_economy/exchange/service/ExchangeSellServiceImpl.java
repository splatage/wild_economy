package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.EconomyResult;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellLineResult;
import com.splatage.wild_economy.exchange.domain.SellPreviewLine;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import com.splatage.wild_economy.exchange.domain.SellQuote;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

public final class ExchangeSellServiceImpl implements ExchangeSellService {

    private static final int CONTAINER_TARGET_RANGE = 5;
    private static final String LOCKED_CONTAINER_MESSAGE = "That container is locked.";

    private final ExchangeCatalog exchangeCatalog;
    private final ItemValidationService itemValidationService;
    private final StockService stockService;
    private final PricingService pricingService;
    private final EconomyGateway economyGateway;
    private final TransactionLogService transactionLogService;
    private final ContainerAccessService containerAccessService;

    public ExchangeSellServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final ItemValidationService itemValidationService,
        final StockService stockService,
        final PricingService pricingService,
        final EconomyGateway economyGateway,
        final TransactionLogService transactionLogService
    ) {
        this(
            exchangeCatalog,
            itemValidationService,
            stockService,
            pricingService,
            economyGateway,
            transactionLogService,
            ContainerAccessServices.createDefault()
        );
    }

    ExchangeSellServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final ItemValidationService itemValidationService,
        final StockService stockService,
        final PricingService pricingService,
        final EconomyGateway economyGateway,
        final TransactionLogService transactionLogService,
        final ContainerAccessService containerAccessService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.itemValidationService = Objects.requireNonNull(itemValidationService, "itemValidationService");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.economyGateway = Objects.requireNonNull(economyGateway, "economyGateway");
        this.transactionLogService = Objects.requireNonNull(transactionLogService, "transactionLogService");
        this.containerAccessService = Objects.requireNonNull(containerAccessService, "containerAccessService");
    }

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, "Player is not online");
        }

        final HeldItemPlanning heldPlanning = this.planHeldItem(player.getInventory().getItemInMainHand());
        if (!heldPlanning.success()) {
            return new SellHandResult(false, null, heldPlanning.rejectionReason(), heldPlanning.message());
        }

        final SalePlanning planning = heldPlanning.planning();
        final ItemStack restoreStack = player.getInventory().getItemInMainHand().clone();
        player.getInventory().setItemInMainHand(null);

        final EconomyResult payout = this.economyGateway.deposit(playerId, planning.totalEarned());
        if (!payout.success()) {
            player.getInventory().setItemInMainHand(restoreStack);
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, payout.message());
        }

        final List<SellLineResult> soldLines = this.completePlannedSales(playerId, planning.plannedSales());
        final SellLineResult lineResult = soldLines.getFirst();
        final String message = lineResult.tapered()
            ? "Sold " + lineResult.amountSold() + "x " + lineResult.displayName() + " for " + lineResult.totalEarned()
                + " (reduced due to high stock)"
            : "Sold " + lineResult.amountSold() + "x " + lineResult.displayName() + " for " + lineResult.totalEarned();

        return new SellHandResult(true, lineResult, null, message);
    }

    @Override
    public SellPreviewResult previewInventorySell(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellPreviewResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        final SalePlanning planning = this.planSalesFromInventory(
            player.getInventory(),
            true,
            "protected container item; use /sellcontainer"
        );
        if (planning.plannedSales().isEmpty()) {
            return new SellPreviewResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "No sellable items found"
            );
        }

        return this.toPreviewResult(planning, "Sell preview:");
    }

    @Override
    public SellPreviewResult previewContainerSell(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellPreviewResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        final Block targetBlock = player.getTargetBlockExact(CONTAINER_TARGET_RANGE);
        final SupportedContainerTarget blockTarget = this.resolveSupportedBlockTarget(targetBlock);
        if (blockTarget != null) {
            if (this.isLocked(blockTarget.state())) {
                return this.containerPreviewFailure(blockTarget.description(), LOCKED_CONTAINER_MESSAGE);
            }

            final ContainerAccessResult accessResult = this.canAccessPlacedContainer(player, blockTarget.block());
            if (!accessResult.allowed()) {
                return this.containerPreviewFailure(blockTarget.description(), accessResult.message());
            }

            return this.previewFromInventoryTarget(blockTarget.inventory(), blockTarget.description());
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        if (this.isHeldShulkerItem(held)) {
            return this.previewFromHeldShulker(held);
        }

        return new SellPreviewResult(
            false,
            List.of(),
            BigDecimal.ZERO,
            List.of(),
            "No supported container found. Look at a chest, barrel, or shulker, or hold a shulker box."
        );
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
        this.removePlannedItems(inventory, planning);

        final EconomyResult payout = this.economyGateway.deposit(playerId, planning.totalEarned());
        if (!payout.success()) {
            this.restorePlannedItems(inventory, planning);
            return new SellAllResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "Payout failed: " + payout.message()
            );
        }

        final List<SellLineResult> soldLines = this.completePlannedSales(playerId, planning.plannedSales());
        String message = "Sold " + soldLines.size() + " item type(s) for a total of " + planning.totalEarned();
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
            if (this.isLocked(blockTarget.state())) {
                return this.deniedPlacedContainer(blockTarget.description(), LOCKED_CONTAINER_MESSAGE);
            }

            final ContainerAccessResult accessResult = this.canAccessPlacedContainer(player, blockTarget.block());
            if (!accessResult.allowed()) {
                return this.deniedPlacedContainer(blockTarget.description(), accessResult.message());
            }

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

    boolean isSupportedPlacedContainerTarget(final Block targetBlock) {
        return this.resolveSupportedBlockTarget(targetBlock) != null;
    }

    ContainerAccessResult canAccessPlacedContainer(final Player player, final Block targetBlock) {
        return this.containerAccessService.canAccessPlacedContainer(player, targetBlock);
    }

    SellContainerResult buildPlacedContainerDeniedResult(final Block targetBlock, final String message) {
        return this.deniedPlacedContainer(this.describeTargetBlock(targetBlock), message);
    }

    SellContainerResult sellPlacedContainerAtLocation(final UUID playerId, final Location location) {
        if (location == null || location.getWorld() == null) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                null,
                "No supported container found. It may have changed before the sale completed."
            );
        }

        final SupportedContainerTarget blockTarget = this.resolveSupportedBlockTarget(location.getBlock());
        if (blockTarget == null) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                null,
                "No supported container found. It may have changed before the sale completed."
            );
        }

        if (this.isLocked(blockTarget.state())) {
            return this.deniedPlacedContainer(blockTarget.description(), LOCKED_CONTAINER_MESSAGE);
        }

        return this.sellFromInventoryTarget(playerId, blockTarget.inventory(), blockTarget.description());
    }

    private SellPreviewResult previewFromInventoryTarget(final Inventory inventory, final String targetDescription) {
        final SalePlanning planning = this.planSalesFromInventory(inventory, true, "nested container not supported");
        if (planning.plannedSales().isEmpty()) {
            return new SellPreviewResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "No sellable items found in " + targetDescription + "."
            );
        }

        return this.toPreviewResult(planning, "Sell preview for " + targetDescription + ":");
    }

    private SellPreviewResult previewFromHeldShulker(final ItemStack heldShulker) {
        final BlockStateMeta blockStateMeta = (BlockStateMeta) heldShulker.getItemMeta();
        if (blockStateMeta == null || !(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return new SellPreviewResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                "Held shulker box is not readable."
            );
        }

        final SalePlanning planning = this.planSalesFromInventory(
            shulkerBox.getInventory(),
            true,
            "nested container not supported"
        );
        if (planning.plannedSales().isEmpty()) {
            return new SellPreviewResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                planning.skippedDescriptions(),
                "No sellable items found in held shulker box."
            );
        }

        return this.toPreviewResult(planning, "Sell preview for held shulker box:");
    }

    private SellPreviewResult containerPreviewFailure(final String targetDescription, final String message) {
        final String prefix = targetDescription == null || targetDescription.isBlank()
            ? "Container preview unavailable"
            : "Container preview unavailable for " + targetDescription;
        return new SellPreviewResult(false, List.of(), BigDecimal.ZERO, List.of(), prefix + ": " + message);
    }

    private SellContainerResult sellFromInventoryTarget(
        final UUID playerId,
        final Inventory inventory,
        final String targetDescription
    ) {
        final SalePlanning planning = this.planSalesFromInventory(inventory, true, "nested container not supported");
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

        this.removePlannedItems(inventory, planning);

        final EconomyResult payout = this.economyGateway.deposit(playerId, planning.totalEarned());
        if (!payout.success()) {
            this.restorePlannedItems(inventory, planning);
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

        this.removePlannedItems(shulkerBox.getInventory(), planning);

        final ItemStack updatedHeld = heldShulker.clone();
        final BlockStateMeta updatedMeta = (BlockStateMeta) updatedHeld.getItemMeta();
        if (updatedMeta == null) {
            this.restorePlannedItems(shulkerBox.getInventory(), planning);
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

    private List<SellLineResult> completePlannedSales(
        final UUID playerId,
        final List<GroupedPlannedSale> plannedSales
    ) {
        final List<SellLineResult> soldLines = new ArrayList<>(plannedSales.size());
        for (final GroupedPlannedSale sale : plannedSales) {
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
        final Map<ItemKey, GroupedSaleAccumulator> groupedSales = new LinkedHashMap<>();
        final List<String> skippedDescriptions = new ArrayList<>();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            if (protectShulkers && this.isShulkerBoxItem(stack.getType())) {
                skippedDescriptions.add(
                    this.friendlyMaterialName(stack.getType()) + " x" + stack.getAmount() + " (" + protectedShulkerReason + ")"
                );
                continue;
            }

            final ValidationResult validation = this.itemValidationService.validateForSell(stack);
            if (!validation.valid()) {
                skippedDescriptions.add(this.describeSkippedStack(stack, validation.detail()));
                continue;
            }

            final ItemKey itemKey = validation.itemKey();
            final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
                .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

            groupedSales
                .computeIfAbsent(itemKey, key -> new GroupedSaleAccumulator(itemKey, entry.displayName()))
                .add(slot, stack);
        }

        final List<GroupedPlannedSale> plannedSales = new ArrayList<>(groupedSales.size());
        BigDecimal totalEarned = BigDecimal.ZERO;
        boolean taperedAny = false;

        for (final GroupedSaleAccumulator accumulator : groupedSales.values()) {
            final StockSnapshot stockSnapshot = this.stockService.getSnapshot(accumulator.itemKey());
            final SellQuote quote = this.pricingService.quoteSell(
                accumulator.itemKey(),
                accumulator.totalAmount(),
                stockSnapshot
            );
            if (quote.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
                skippedDescriptions.add(accumulator.displayName() + " x" + accumulator.totalAmount() + " (zero value)");
                continue;
            }

            plannedSales.add(accumulator.toPlannedSale(quote, stockSnapshot));
            totalEarned = totalEarned.add(quote.totalPrice());
            taperedAny |= quote.tapered();
        }

        return new SalePlanning(
            List.copyOf(plannedSales),
            List.copyOf(skippedDescriptions),
            totalEarned.setScale(2, RoundingMode.HALF_UP),
            taperedAny
        );
    }

    private HeldItemPlanning planHeldItem(final ItemStack held) {
        final ValidationResult validation = this.itemValidationService.validateForSell(held);
        if (!validation.valid()) {
            return new HeldItemPlanning(null, validation.rejectionReason(), validation.detail());
        }

        final ItemKey itemKey = validation.itemKey();
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final int amount = held.getAmount();
        final StockSnapshot stockSnapshot = this.stockService.getSnapshot(itemKey);
        final SellQuote quote = this.pricingService.quoteSell(itemKey, amount, stockSnapshot);
        if (quote.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return new HeldItemPlanning(null, RejectionReason.SELL_NOT_ALLOWED, "Sell value is zero");
        }

        return new HeldItemPlanning(
            new SalePlanning(
                List.of(new GroupedPlannedSale(itemKey, entry.displayName(), amount, quote, stockSnapshot, List.of())),
                List.of(),
                quote.totalPrice().setScale(2, RoundingMode.HALF_UP),
                quote.tapered()
            ),
            null,
            null
        );
    }

    private SellPreviewResult toPreviewResult(final SalePlanning planning, final String message) {
        final List<SellPreviewLine> lines = new ArrayList<>(planning.plannedSales().size());
        for (final GroupedPlannedSale sale : planning.plannedSales()) {
            lines.add(new SellPreviewLine(
                sale.itemKey(),
                sale.displayName(),
                sale.amount(),
                sale.quote().effectiveUnitPrice(),
                sale.quote().totalPrice(),
                sale.stockSnapshot().stockState(),
                sale.quote().tapered()
            ));
        }
        return new SellPreviewResult(true, List.copyOf(lines), planning.totalEarned(), planning.skippedDescriptions(), message);
    }

    private String describeSkippedStack(final ItemStack stack, final String reason) {
        final String detail = reason == null || reason.isBlank() ? "not sellable" : reason;
        return this.friendlyMaterialName(stack.getType()) + " x" + stack.getAmount() + " (" + detail + ")";
    }

    private void removePlannedItems(final Inventory inventory, final SalePlanning planning) {
        for (final GroupedPlannedSale sale : planning.plannedSales()) {
            for (final InventoryRemoval removal : sale.removals()) {
                inventory.setItem(removal.slot(), null);
            }
        }
    }

    private void restorePlannedItems(final Inventory inventory, final SalePlanning planning) {
        for (final GroupedPlannedSale sale : planning.plannedSales()) {
            for (final InventoryRemoval removal : sale.removals()) {
                inventory.setItem(removal.slot(), removal.originalStack());
            }
        }
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

    private SellContainerResult deniedPlacedContainer(final String targetDescription, final String message) {
        return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), targetDescription, message);
    }

    private String describeTargetBlock(final Block targetBlock) {
        if (targetBlock == null) {
            return null;
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

    private record SalePlanning(
        List<GroupedPlannedSale> plannedSales,
        List<String> skippedDescriptions,
        BigDecimal totalEarned,
        boolean taperedAny
    ) {
    }

    private record GroupedPlannedSale(
        ItemKey itemKey,
        String displayName,
        int amount,
        SellQuote quote,
        StockSnapshot stockSnapshot,
        List<InventoryRemoval> removals
    ) {
    }

    private record HeldItemPlanning(SalePlanning planning, RejectionReason rejectionReason, String message) {
        private boolean success() {
            return this.planning != null;
        }
    }

    private record InventoryRemoval(int slot, ItemStack originalStack) {
    }

    private record SupportedContainerTarget(Block block, BlockState state, Inventory inventory, String description) {
    }

    private static final class GroupedSaleAccumulator {

        private final ItemKey itemKey;
        private final String displayName;
        private final List<InventoryRemoval> removals;
        private int totalAmount;

        private GroupedSaleAccumulator(final ItemKey itemKey, final String displayName) {
            this.itemKey = itemKey;
            this.displayName = displayName;
            this.removals = new ArrayList<>();
            this.totalAmount = 0;
        }

        private void add(final int slot, final ItemStack originalStack) {
            this.removals.add(new InventoryRemoval(slot, originalStack.clone()));
            this.totalAmount += originalStack.getAmount();
        }

        private ItemKey itemKey() {
            return this.itemKey;
        }

        private String displayName() {
            return this.displayName;
        }

        private int totalAmount() {
            return this.totalAmount;
        }

        private GroupedPlannedSale toPlannedSale(final SellQuote quote, final StockSnapshot stockSnapshot) {
            return new GroupedPlannedSale(
                this.itemKey,
                this.displayName,
                this.totalAmount,
                quote,
                stockSnapshot,
                List.copyOf(this.removals)
            );
        }
    }
}
