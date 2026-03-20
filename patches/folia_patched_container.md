# wild_economy Folia placed-container follow-up patchset

Source-of-truth snapshot: `c4af44c5b465964a6abc42abf92b8446c11e007f`

This patchset continues the current Folia migration branch.

What it changes:

1. adds `runOnLocation(...)` to the platform executor,
2. adds a dedicated `FoliaContainerSellCoordinator` for `/sellcontainer`,
3. keeps held-shulker selling on the player scheduler,
4. moves placed-container inventory mutation onto the owning region scheduler,
5. splits placed-container protection probing from region-owned block-state mutation.

This is the next step after the current branch state, where command entry points and GUI buy clicks are already routed onto the player scheduler, but placed world-container selling is still performed inline inside `ExchangeSellServiceImpl`.

---

## File: `src/main/java/com/splatage/wild_economy/platform/PlatformExecutor.java`

```java
package com.splatage.wild_economy.platform;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface PlatformExecutor {

    void runOnPlayer(Player player, Runnable task);

    void runOnLocation(Location location, Runnable task);

    void runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks);

    void cancelPluginTasks();
}
```

---

## File: `src/main/java/com/splatage/wild_economy/platform/PaperFoliaPlatformExecutor.java`

```java
package com.splatage.wild_economy.platform;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PaperFoliaPlatformExecutor implements PlatformExecutor {

    private final Plugin plugin;

    public PaperFoliaPlatformExecutor(final Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void runOnPlayer(final Player player, final Runnable task) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
            return;
        }

        player.getScheduler().run(this.plugin, scheduledTask -> task.run(), null);
    }

    @Override
    public void runOnLocation(final Location location, final Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");

        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location.world");
        }

        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
            return;
        }

        this.plugin.getServer().getRegionScheduler().execute(this.plugin, location, task);
    }

    @Override
    public void runGlobalRepeating(final Runnable task, final long initialDelayTicks, final long periodTicks) {
        Objects.requireNonNull(task, "task");

        this.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
            this.plugin,
            scheduledTask -> task.run(),
            initialDelayTicks,
            periodTicks
        );
    }

    @Override
    public void cancelPluginTasks() {
        this.plugin.getServer().getGlobalRegionScheduler().cancelTasks(this.plugin);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/integration/protection/ContainerAccessService.java`

```java
package com.splatage.wild_economy.integration.protection;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface ContainerAccessService {

    ContainerAccessResult canAccessPlacedContainer(Player player, Block targetBlock);
}
```

---

## File: `src/main/java/com/splatage/wild_economy/integration/protection/EventDrivenContainerAccessService.java`

```java
package com.splatage.wild_economy.integration.protection;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class EventDrivenContainerAccessService implements ContainerAccessService {

    private static final String PROTECTED_CONTAINER_MESSAGE = "You cannot use /shop sellcontainer on that protected container.";
    private static final String UNKNOWN_CONTAINER_MESSAGE = "Could not verify access to that container, so the sale was cancelled.";

    @Override
    public ContainerAccessResult canAccessPlacedContainer(final Player player, final Block targetBlock) {
        if (player == null || targetBlock == null) {
            return ContainerAccessResult.deny(UNKNOWN_CONTAINER_MESSAGE);
        }

        final PlayerInteractEvent probe = new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_BLOCK,
            player.getInventory().getItemInMainHand(),
            targetBlock,
            BlockFace.UP,
            EquipmentSlot.HAND
        );

        try {
            Bukkit.getPluginManager().callEvent(probe);
        } catch (final Throwable ignored) {
            return ContainerAccessResult.deny(UNKNOWN_CONTAINER_MESSAGE);
        }

        if (this.isDenied(probe)) {
            return ContainerAccessResult.deny(PROTECTED_CONTAINER_MESSAGE);
        }

        return ContainerAccessResult.allow();
    }

    @SuppressWarnings("deprecation")
    private boolean isDenied(final PlayerInteractEvent probe) {
        return probe.useInteractedBlock() == Event.Result.DENY || probe.isCancelled();
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/exchange/service/FoliaContainerSellCoordinator.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.integration.protection.ContainerAccessResult;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class FoliaContainerSellCoordinator {

    private static final int CONTAINER_TARGET_RANGE = 5;

    private final PlatformExecutor platformExecutor;
    private final ExchangeService exchangeService;
    private final ExchangeSellServiceImpl exchangeSellService;

    public FoliaContainerSellCoordinator(
        final PlatformExecutor platformExecutor,
        final ExchangeService exchangeService,
        final ExchangeSellServiceImpl exchangeSellService
    ) {
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.exchangeSellService = Objects.requireNonNull(exchangeSellService, "exchangeSellService");
    }

    public void sellContainer(final Player player, final Consumer<SellContainerResult> callback) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(callback, "callback");

        this.platformExecutor.runOnPlayer(player, () -> this.sellContainerOnPlayer(player, callback));
    }

    private void sellContainerOnPlayer(final Player player, final Consumer<SellContainerResult> callback) {
        final Block targetBlock = player.getTargetBlockExact(CONTAINER_TARGET_RANGE);
        if (!this.exchangeSellService.isSupportedPlacedContainerTarget(targetBlock)) {
            callback.accept(this.exchangeService.sellContainer(player.getUniqueId()));
            return;
        }

        final ContainerAccessResult accessResult = this.exchangeSellService.canAccessPlacedContainer(player, targetBlock);
        if (!accessResult.allowed()) {
            callback.accept(this.exchangeSellService.buildPlacedContainerDeniedResult(targetBlock, accessResult.message()));
            return;
        }

        final Location targetLocation = targetBlock.getLocation().clone();
        this.platformExecutor.runOnLocation(targetLocation, () -> {
            final SellContainerResult result = this.exchangeSellService.sellPlacedContainerAtLocation(
                player.getUniqueId(),
                targetLocation
            );
            this.platformExecutor.runOnPlayer(player, () -> callback.accept(result));
        });
    }
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
import com.splatage.wild_economy.integration.protection.ContainerAccessResult;
import com.splatage.wild_economy.integration.protection.ContainerAccessService;
import com.splatage.wild_economy.integration.protection.EventDrivenContainerAccessService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
            new EventDrivenContainerAccessService()
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
                skippedDescriptions.add(
                    this.friendlyMaterialName(stack.getType()) + " x" + stack.getAmount() + " (" + protectedShulkerReason + ")"
                );
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
        return new SellContainerResult(
            false,
            List.of(),
            BigDecimal.ZERO,
            List.of(),
            targetDescription,
            message
        );
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
        List<PlannedSale> plannedSales,
        List<String> skippedDescriptions,
        BigDecimal totalEarned,
        boolean taperedAny
    ) {
    }

    private record SupportedContainerTarget(
        Block block,
        BlockState state,
        Inventory inventory,
        String description
    ) {
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellContainerSubcommand.java`

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellLineResult;
import com.splatage.wild_economy.exchange.service.FoliaContainerSellCoordinator;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellContainerSubcommand implements CommandExecutor {

    private final FoliaContainerSellCoordinator sellCoordinator;

    public ShopSellContainerSubcommand(final FoliaContainerSellCoordinator sellCoordinator) {
        this.sellCoordinator = Objects.requireNonNull(sellCoordinator, "sellCoordinator");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        this.sellCoordinator.sellContainer(player, result -> {
            player.sendMessage(result.message());
            this.sendSoldLines(player, result.soldLines());
            this.sendSkippedLines(player, result.skippedDescriptions());
        });
        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender);
    }

    private void sendSoldLines(final Player player, final List<SellLineResult> soldLines) {
        if (soldLines.isEmpty()) {
            return;
        }

        final int maxLines = Math.min(5, soldLines.size());
        for (int i = 0; i < maxLines; i++) {
            final SellLineResult line = soldLines.get(i);
            final String taperSuffix = line.tapered() ? " (reduced)" : "";
            player.sendMessage(" - " + line.amountSold() + "x " + line.displayName() + " for " + line.totalEarned() + taperSuffix);
        }

        if (soldLines.size() > maxLines) {
            player.sendMessage(" - ... and " + (soldLines.size() - maxLines) + " more stack(s)");
        }
    }

    private void sendSkippedLines(final Player player, final List<String> skippedDescriptions) {
        if (skippedDescriptions.isEmpty()) {
            return;
        }

        final int maxSkipped = Math.min(5, skippedDescriptions.size());
        player.sendMessage("Skipped:");
        for (int i = 0; i < maxSkipped; i++) {
            player.sendMessage(" - " + skippedDescriptions.get(i));
        }

        if (skippedDescriptions.size() > maxSkipped) {
            player.sendMessage(" - ... and " + (skippedDescriptions.size() - maxSkipped) + " more skipped stack(s)");
        }
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
import com.splatage.wild_economy.gui.ExchangeItemDetailMenu;
import com.splatage.wild_economy.gui.ExchangeRootMenu;
import com.splatage.wild_economy.gui.ExchangeSubcategoryMenu;
import com.splatage.wild_economy.gui.MenuSessionStore;
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
            this.transactionLogService
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
            new MenuSessionStore(),
            rootMenu,
            subcategoryMenu,
            browseMenu,
            itemDetailMenu
        );

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
        final ShopSellHandSubcommand sellHandSubcommand = new ShopSellHandSubcommand(this.exchangeService, this.platformExecutor);
        final ShopSellAllSubcommand sellAllSubcommand = new ShopSellAllSubcommand(this.exchangeService, this.platformExecutor);
        final ShopSellContainerSubcommand sellContainerSubcommand = new ShopSellContainerSubcommand(
            this.foliaContainerSellCoordinator
        );

        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(
                openSubcommand,
                sellHandSubcommand,
                sellAllSubcommand,
                sellContainerSubcommand
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

        final PluginCommand sellContainer = this.plugin.getCommand("sellcontainer");
        if (sellContainer != null) {
            sellContainer.setExecutor(sellContainerSubcommand);
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand(this.plugin));
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
        this.platformExecutor.cancelPluginTasks();

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

---

## Notes

* This patchset deliberately leaves `FoliaSafeExchangeSellService` in place as a safety fence for any remaining direct `ExchangeService.sellContainer(...)` callers.
* `/sellcontainer` now gets its own coordinator because it is the one sell path that crosses both **player-owned** and **location-owned** execution domains.
* The current protection probing approach still relies on a synthetic `PlayerInteractEvent` fired from the player thread. That keeps protection checks aligned with claim-plugin event hooks, but it remains the main remaining compatibility risk for exotic protection plugins under Folia.
