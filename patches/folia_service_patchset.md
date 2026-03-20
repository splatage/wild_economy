# wild_economy Folia access-service hardening patchset

Source-of-truth snapshot: `3f3cadc8094b4caf5610032b3459fb470bc3c7b5`

This patchset hardens the remaining unsafe part of the current Folia migration.

## Why this step

At this commit, the plugin already has:

* `folia-supported: true`
* global turnover scheduling on the global scheduler
* player-thread routing for command and GUI entry points
* a location-owned execution path for placed-container mutation via `runOnLocation(...)`

However, the current placed-container access check still uses a synthetic `PlayerInteractEvent` in `EventDrivenContainerAccessService`. That event mixes a player-owned context and a block-owned context, which is the last major Folia risk in the current branch.

This patch changes the default `ContainerAccessService` selection so that:

* on **Paper**: keep the current event-driven access probe
* on **Folia**: stop firing the synthetic event probe

  * if no common protection plugin is detected, allow placed-container selling
  * if a common protection/claim plugin is detected, deny placed-container selling with a clear message

This is intentionally conservative. It keeps the core sell path Folia-safe while avoiding unsafe fake-event probing under Folia. It does mean that explicit claim-plugin adapters become the next step for restoring full protected-container support on Folia.

---

## File: `src/main/java/com/splatage/wild_economy/platform/PlatformSupport.java`

```java
package com.splatage.wild_economy.platform;

public final class PlatformSupport {

    private static final boolean FOLIA = detectFolia();

    private PlatformSupport() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/integration/protection/AllowAllContainerAccessService.java`

```java
package com.splatage.wild_economy.integration.protection;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class AllowAllContainerAccessService implements ContainerAccessService {

    @Override
    public ContainerAccessResult canAccessPlacedContainer(final Player player, final Block targetBlock) {
        return ContainerAccessResult.allow();
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/integration/protection/ProtectionPluginAwareFoliaContainerAccessService.java`

```java
package com.splatage.wild_economy.integration.protection;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ProtectionPluginAwareFoliaContainerAccessService implements ContainerAccessService {

    private static final List<String> KNOWN_PROTECTION_PLUGINS = List.of(
        "GriefPrevention",
        "PlotSquared",
        "Towny",
        "Lands",
        "HuskClaims",
        "Residence",
        "Factions",
        "FactionsUUID",
        "SaberFactions",
        "MassiveCore",
        "KingdomsX"
    );

    private final String detectedProtectionPlugin;

    public ProtectionPluginAwareFoliaContainerAccessService() {
        this.detectedProtectionPlugin = this.detectProtectionPlugin();
    }

    @Override
    public ContainerAccessResult canAccessPlacedContainer(final Player player, final Block targetBlock) {
        if (this.detectedProtectionPlugin == null) {
            return ContainerAccessResult.allow();
        }

        return ContainerAccessResult.deny(
            "Placed container selling is temporarily disabled on Folia while protection plugin '"
                + this.detectedProtectionPlugin
                + "' is installed. Held shulker selling still works."
        );
    }

    private String detectProtectionPlugin() {
        for (final String pluginName : KNOWN_PROTECTION_PLUGINS) {
            if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                return pluginName;
            }
        }
        return null;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/integration/protection/ContainerAccessServices.java`

```java
package com.splatage.wild_economy.integration.protection;

import com.splatage.wild_economy.platform.PlatformSupport;

public final class ContainerAccessServices {

    private ContainerAccessServices() {
    }

    public static ContainerAccessService createDefault() {
        if (PlatformSupport.isFolia()) {
            return new ProtectionPluginAwareFoliaContainerAccessService();
        }
        return new EventDrivenContainerAccessService();
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
import com.splatage.wild_economy.integration.protection.ContainerAccessServices;
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

## Notes

* This patch intentionally leaves `EventDrivenContainerAccessService` in the codebase for Paper.
* On Folia, the default container-access strategy is now conservative and no longer fires a synthetic interact event.
* The next step after this patch is to add explicit region-safe claim adapters for GriefPrevention / PlotSquared / Towny / Factions-family plugins so placed-container selling can be restored when those plugins are present.
