# wild_economy implementation patch: shulker protection + `/sellcontainer`

Scope implemented:

* protect shulker boxes from accidental sale during `/sellall`
* add new `/sellcontainer` command
* support looked-at chest / barrel / placed shulker box containers
* support held shulker box item in main hand
* do not recurse into nested containers
* do not sell the container itself
* align touched sell paths with soft-cap behavior by removing hard stock-room rejection

This patch exports complete files for the affected paths.

---

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/SellContainerResult.java`

```java
package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;
import java.util.List;

public record SellContainerResult(
    boolean success,
    List<SellLineResult> soldLines,
    BigDecimal totalEarned,
    List<String> skippedDescriptions,
    String targetDescription,
    String message
) {
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeService.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.List;
import java.util.UUID;

public interface ExchangeService {

    SellHandResult sellHand(UUID playerId);

    SellAllResult sellAll(UUID playerId);

    SellContainerResult sellContainer(UUID playerId);

    BuyResult buy(UUID playerId, ItemKey itemKey, int amount);

    List<ExchangeCatalogView> browseCategory(
        ItemCategory category,
        GeneratedItemCategory generatedCategory,
        int page,
        int pageSize
    );

    int countVisibleItems(ItemCategory category, GeneratedItemCategory generatedCategory);

    List<GeneratedItemCategory> listVisibleSubcategories(ItemCategory category);

    ExchangeItemView getItemView(ItemKey itemKey);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ExchangeServiceImpl implements ExchangeService {

    private final ExchangeBrowseService exchangeBrowseService;
    private final ExchangeBuyService exchangeBuyService;
    private final ExchangeSellService exchangeSellService;

    public ExchangeServiceImpl(
        final ExchangeBrowseService exchangeBrowseService,
        final ExchangeBuyService exchangeBuyService,
        final ExchangeSellService exchangeSellService
    ) {
        this.exchangeBrowseService = Objects.requireNonNull(exchangeBrowseService, "exchangeBrowseService");
        this.exchangeBuyService = Objects.requireNonNull(exchangeBuyService, "exchangeBuyService");
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
    public SellContainerResult sellContainer(final UUID playerId) {
        return this.exchangeSellService.sellContainer(playerId);
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        return this.exchangeBuyService.buy(playerId, itemKey, amount);
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final int pageSize
    ) {
        return this.exchangeBrowseService.browseCategory(category, generatedCategory, page, pageSize);
    }

    @Override
    public int countVisibleItems(final ItemCategory category, final GeneratedItemCategory generatedCategory) {
        return this.exchangeBrowseService.countVisibleItems(category, generatedCategory);
    }

    @Override
    public List<GeneratedItemCategory> listVisibleSubcategories(final ItemCategory category) {
        return this.exchangeBrowseService.listVisibleSubcategories(category);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        return this.exchangeBrowseService.getItemView(itemKey);
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellService.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.UUID;

public interface ExchangeSellService {

    SellHandResult sellHand(UUID playerId);

    SellAllResult sellAll(UUID playerId);

    SellContainerResult sellContainer(UUID playerId);
}
```

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
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopSellContainerSubcommand.java`

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellLineResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellContainerSubcommand implements CommandExecutor {

    private final ExchangeService exchangeService;

    public ShopSellContainerSubcommand(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        final SellContainerResult result = this.exchangeService.sellContainer(player.getUniqueId());
        player.sendMessage(result.message());
        this.sendSoldLines(player, result.soldLines());
        this.sendSkippedLines(player, result.skippedDescriptions());
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

## File: `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`

```java
package com.splatage.wild_economy.command;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopCommand implements CommandExecutor {

    private final ShopOpenSubcommand openSubcommand;
    private final ShopSellHandSubcommand sellHandSubcommand;
    private final ShopSellAllSubcommand sellAllSubcommand;
    private final ShopSellContainerSubcommand sellContainerSubcommand;

    public ShopCommand(
        final ShopOpenSubcommand openSubcommand,
        final ShopSellHandSubcommand sellHandSubcommand,
        final ShopSellAllSubcommand sellAllSubcommand,
        final ShopSellContainerSubcommand sellContainerSubcommand
    ) {
        this.openSubcommand = Objects.requireNonNull(openSubcommand, "openSubcommand");
        this.sellHandSubcommand = Objects.requireNonNull(sellHandSubcommand, "sellHandSubcommand");
        this.sellAllSubcommand = Objects.requireNonNull(sellAllSubcommand, "sellAllSubcommand");
        this.sellContainerSubcommand = Objects.requireNonNull(sellContainerSubcommand, "sellContainerSubcommand");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            return this.openSubcommand.execute(sender);
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "sellhand" -> this.sellHandSubcommand.execute(sender);
            case "sellall" -> this.sellAllSubcommand.execute(sender);
            case "sellcontainer" -> this.sellContainerSubcommand.execute(sender);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /shop, /shop sellhand, /shop sellall, /shop sellcontainer, /sellhand, /sellall, or /sellcontainer.");
                yield true;
            }
        };
    }
}
```

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
import com.splatage.wild_economy.gui.ShopMenuListener;
import com.splatage.wild_economy.gui.ShopMenuRouter;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import java.io.File;
import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

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

    public ServiceRegistry(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
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

        this.exchangeBuyService = new ExchangeBuyServiceImpl(
            this.exchangeCatalog,
            this.itemValidationService,
            this.stockService,
            this.pricingService,
            this.economyGateway,
            this.transactionLogService
        );

        this.exchangeSellService = new ExchangeSellServiceImpl(
            this.exchangeCatalog,
            this.itemValidationService,
            this.stockService,
            this.pricingService,
            this.economyGateway,
            this.transactionLogService
        );

        this.exchangeService = new ExchangeServiceImpl(
            this.exchangeBrowseService,
            this.exchangeBuyService,
            this.exchangeSellService
        );

        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(this.exchangeService);
        final ExchangeSubcategoryMenu subcategoryMenu = new ExchangeSubcategoryMenu(this.exchangeService);
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService);
        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService);

        this.shopMenuRouter = new ShopMenuRouter(rootMenu, subcategoryMenu, browseMenu, itemDetailMenu);

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
        final ShopSellHandSubcommand sellHandSubcommand = new ShopSellHandSubcommand(this.exchangeService);
        final ShopSellAllSubcommand sellAllSubcommand = new ShopSellAllSubcommand(this.exchangeService);
        final ShopSellContainerSubcommand sellContainerSubcommand = new ShopSellContainerSubcommand(this.exchangeService);

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
        this.plugin.getServer().getScheduler().runTaskTimer(
            this.plugin,
            new com.splatage.wild_economy.scheduler.StockTurnoverTask(this.stockTurnoverService),
            this.globalConfig.turnoverIntervalTicks(),
            this.globalConfig.turnoverIntervalTicks()
        );
    }

    public void shutdown() {
        this.plugin.getServer().getScheduler().cancelTasks(this.plugin);

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

## File: `src/main/resources/plugin.yml`

```yaml
name: wild_economy
version: ${version}
main: com.splatage.wild_economy.WildEconomyPlugin
api-version: '1.21'
author: splatage
description: Curated Exchange-first economy plugin for Minecraft

depend:
  - Vault

softdepend:
  - Essentials

commands:
  shop:
    description: Open the shop or use sell commands.
    usage: /shop [sellhand|sellall|sellcontainer]
    aliases: [exchange]
    permission: wild_economy.shop
  sellhand:
    description: Sell the item in your hand to the Exchange.
    usage: /sellhand
    permission: wild_economy.shop.sell
  sellall:
    description: Sell all sellable items in your inventory to the Exchange.
    usage: /sellall
    permission: wild_economy.shop.sell
  sellcontainer:
    description: Sell the contents of a looked-at container or held shulker box.
    usage: /sellcontainer
    permission: wild_economy.shop.sellcontainer
  shopadmin:
    description: Admin commands for wild_economy.
    usage: /shopadmin <reload|generatecatalog>
    permission: wild_economy.admin

permissions:
  wild_economy.shop:
    description: Allows use of player shop commands.
    default: true
  wild_economy.shop.sell:
    description: Allows selling to the Exchange.
    default: true
  wild_economy.shop.sellcontainer:
    description: Allows selling the contents of supported containers.
    default: true
  wild_economy.shop.buy:
    description: Allows buying from the Exchange.
    default: true
  wild_economy.admin:
    description: Allows administrative use of wild_economy.
    default: op
```
