# wild_economy — Commit 3 Copy-Ready Files (Buy / Browse Path)

## Status

This document contains **copy-ready contents** for **Commit 3**.

Commit 3 scope:

* browse/category/item-detail view model support
* buy-side validation and purchase flow
* first real `/shop` GUI root
* category browse GUI
* item detail buy GUI
* `/shop` opens the root menu instead of printing a placeholder

This is the first commit that makes `wild_economy` feel like a real Exchange product rather than only a sell backend.

---

## Important scope note

This commit intentionally keeps the GUI layer **thin**.

It does **not** try to add:

* fancy pagination UX
* live async refresh complexity
* admin GUI
* reloadable menu session framework
* dynamic buy pricing

The goal is:

* open `/shop`
* browse categories
* open one category
* click one item
* choose quantity
* buy if stock and money allow

---

# 1. New domain result: buy outcome details

## File: `src/main/java/com/splatage/wild_economy/exchange/domain/BuyResult.java`

Replace the earlier simple version with this richer version.

```java
package com.splatage.wild_economy.exchange.domain;

import java.math.BigDecimal;

public record BuyResult(
    boolean success,
    ItemKey itemKey,
    int amountBought,
    BigDecimal unitPrice,
    BigDecimal totalCost,
    RejectionReason rejectionReason,
    String message
) {}
```

---

# 2. Browse service

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseService.java`

Replace the empty interface with this version.

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.List;

public interface ExchangeBrowseService {
    List<ExchangeCatalogView> browseCategory(ItemCategory category, int page, int pageSize);
    ExchangeItemView getItemView(ItemKey itemKey);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseServiceImpl.java`

Create this new file.

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ExchangeBrowseServiceImpl implements ExchangeBrowseService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;

    public ExchangeBrowseServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(final ItemCategory category, final int page, final int pageSize) {
        final int safePage = Math.max(0, page);
        final int safePageSize = Math.max(1, pageSize);

        return this.exchangeCatalog.byCategory(category).stream()
            .filter(ExchangeCatalogEntry::buyEnabled)
            .skip((long) safePage * safePageSize)
            .limit(safePageSize)
            .map(entry -> {
                final var snapshot = this.stockService.getSnapshot(entry.itemKey());
                return new ExchangeCatalogView(
                    entry.itemKey(),
                    entry.displayName(),
                    entry.buyPrice(),
                    snapshot.stockCount(),
                    snapshot.stockState()
                );
            })
            .collect(Collectors.toList());
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));
        final var snapshot = this.stockService.getSnapshot(itemKey);

        return new ExchangeItemView(
            itemKey,
            entry.displayName(),
            entry.buyPrice(),
            snapshot.stockCount(),
            snapshot.stockCap(),
            snapshot.stockState(),
            entry.buyEnabled()
        );
    }
}
```

---

# 3. Buy service

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyService.java`

Replace the empty interface with this version.

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.UUID;

public interface ExchangeBuyService {
    BuyResult buy(UUID playerId, ItemKey itemKey, int amount);
}
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyServiceImpl.java`

Replace the empty stub with this version.

```java
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
```

---

# 4. Update `ExchangeServiceImpl.java`

Replace the Commit 2D version with this Commit 3 version.

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
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
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        return this.exchangeBuyService.buy(playerId, itemKey, amount);
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(final ItemCategory category, final int page, final int pageSize) {
        return this.exchangeBrowseService.browseCategory(category, page, pageSize);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        return this.exchangeBrowseService.getItemView(itemKey);
    }
}
```

---

# 5. GUI router and session

## File: `src/main/java/com/splatage/wild_economy/gui/MenuSession.java`

Replace the empty stub with this simple version.

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import java.util.UUID;

public record MenuSession(
    UUID playerId,
    ItemCategory currentCategory,
    int currentPage
) {}
```

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuRouter.java`

Replace the empty stub with this version.

```java
package com.splatage.wild_economy.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class ShopMenuRouter {

    private final ExchangeRootMenu exchangeRootMenu;
    private final Map<UUID, MenuSession> sessions = new HashMap<>();

    public ShopMenuRouter(final ExchangeRootMenu exchangeRootMenu) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
    }

    public void openRoot(final Player player) {
        this.sessions.put(player.getUniqueId(), new MenuSession(player.getUniqueId(), null, 0));
        this.exchangeRootMenu.open(player);
    }

    public void updateSession(final MenuSession session) {
        this.sessions.put(session.playerId(), session);
    }

    public MenuSession getSession(final UUID playerId) {
        return this.sessions.get(playerId);
    }
}
```

---

# 6. Root menu

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeRootMenu.java`

Replace the earlier stub with this version.

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeRootMenu {

    private final ExchangeBrowseMenu exchangeBrowseMenu;

    public ExchangeRootMenu(final ExchangeBrowseMenu exchangeBrowseMenu) {
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
    }

    public void open(final Player player) {
        final Inventory inventory = Bukkit.createInventory(null, 27, "Shop");
        inventory.setItem(10, this.button(Material.WHEAT, "Farming"));
        inventory.setItem(11, this.button(Material.IRON_PICKAXE, "Mining"));
        inventory.setItem(12, this.button(Material.BONE, "Mob Drops"));
        inventory.setItem(14, this.button(Material.BRICKS, "Building"));
        inventory.setItem(15, this.button(Material.CHEST, "Utility"));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> this.exchangeBrowseMenu.open(player, ItemCategory.FARMING, 0);
            case 11 -> this.exchangeBrowseMenu.open(player, ItemCategory.MINING, 0);
            case 12 -> this.exchangeBrowseMenu.open(player, ItemCategory.MOB_DROPS, 0);
            case 14 -> this.exchangeBrowseMenu.open(player, ItemCategory.BUILDING, 0);
            case 15 -> this.exchangeBrowseMenu.open(player, ItemCategory.UTILITY, 0);
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    private ItemStack button(final Material material, final String name) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
```

---

# 7. Browse menu

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeBrowseMenu.java`

Replace the earlier stub with this version.

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.service.ExchangeCatalogView;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeBrowseMenu {

    private final ExchangeService exchangeService;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;

    public ExchangeBrowseMenu(
        final ExchangeService exchangeService,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    public void open(final Player player, final ItemCategory category, final int page) {
        final Inventory inventory = Bukkit.createInventory(null, 54, "Shop - " + this.prettyCategory(category));
        final List<ExchangeCatalogView> entries = this.exchangeService.browseCategory(category, page, 45);

        int slot = 0;
        for (final ExchangeCatalogView view : entries) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, this.catalogItem(view));
            slot++;
        }

        inventory.setItem(45, this.button(Material.ARROW, "Back"));
        inventory.setItem(49, this.button(Material.BARRIER, "Close"));
        inventory.setItem(53, this.button(Material.ARROW, "Next"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final ItemCategory category, final int page) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta() || !clicked.getItemMeta().hasLocalizedName()) {
                return;
            }
            final String itemKeyValue = clicked.getItemMeta().getLocalizedName();
            this.exchangeItemDetailMenu.open(player, new ItemKey(itemKeyValue), 1);
            return;
        }

        switch (slot) {
            case 45 -> player.closeInventory();
            case 49 -> player.closeInventory();
            case 53 -> this.open(player, category, page + 1);
            default -> {
            }
        }
    }

    private ItemStack catalogItem(final ExchangeCatalogView view) {
        final Material material = this.resolveMaterial(view.itemKey());
        final ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(view.displayName());
            meta.setLocalizedName(view.itemKey().value());
            meta.setLore(List.of(
                "Price: " + view.buyPrice(),
                "Stock: " + view.stockCount(),
                "State: " + view.stockState().name()
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack button(final Material material, final String name) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material resolveMaterial(final ItemKey itemKey) {
        return Material.matchMaterial(itemKey.value().replace("minecraft:", "").toUpperCase());
    }

    private String prettyCategory(final ItemCategory category) {
        return switch (category) {
            case FARMING -> "Farming";
            case MINING -> "Mining";
            case MOB_DROPS -> "Mob Drops";
            case BUILDING -> "Building";
            case UTILITY -> "Utility";
        };
    }
}
```

---

# 8. Item detail menu

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeItemDetailMenu.java`

Replace the earlier stub with this version.

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.service.ExchangeItemView;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeItemDetailMenu {

    private final ExchangeService exchangeService;

    public ExchangeItemDetailMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void open(final Player player, final ItemKey itemKey, final int amount) {
        final ExchangeItemView view = this.exchangeService.getItemView(itemKey);
        final Inventory inventory = Bukkit.createInventory(null, 27, "Buy - " + view.displayName());

        inventory.setItem(11, this.detailItem(view, amount));
        inventory.setItem(13, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 1"));
        inventory.setItem(14, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 8"));
        inventory.setItem(15, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 64"));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));
        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final ItemKey itemKey) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        final int amount = switch (slot) {
            case 13 -> 1;
            case 14 -> 8;
            case 15 -> 64;
            default -> 0;
        };

        if (slot == 22) {
            player.closeInventory();
            return;
        }

        if (amount > 0) {
            final BuyResult result = this.exchangeService.buy(player.getUniqueId(), itemKey, amount);
            player.sendMessage(result.message());
            if (result.success()) {
                this.open(player, itemKey, 1);
            }
        }
    }

    private ItemStack detailItem(final ExchangeItemView view, final int amount) {
        final Material material = this.resolveMaterial(view.itemKey());
        final ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material, Math.max(1, Math.min(amount, 64)));
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(view.displayName());
            meta.setLocalizedName(view.itemKey().value());
            meta.setLore(List.of(
                "Unit price: " + view.buyPrice(),
                "Stock: " + view.stockCount(),
                "State: " + view.stockState().name()
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack button(final Material material, final String name) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material resolveMaterial(final ItemKey itemKey) {
        return Material.matchMaterial(itemKey.value().replace("minecraft:", "").toUpperCase());
    }
}
```

---

# 9. Listener for inventory clicks

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuListener.java`

Create this new file.

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class ShopMenuListener implements Listener {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;

    public ShopMenuListener(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null) {
            return;
        }

        if (title.equals("Shop")) {
            this.exchangeRootMenu.handleClick(event);
            return;
        }

        if (title.startsWith("Shop - ")) {
            final ItemCategory category = this.parseCategory(title.substring("Shop - ".length()));
            if (category != null) {
                this.exchangeBrowseMenu.handleClick(event, category, 0);
            }
            return;
        }

        if (title.startsWith("Buy - ")) {
            final var current = event.getInventory().getItem(11);
            if (current != null && current.hasItemMeta() && current.getItemMeta().hasLocalizedName()) {
                this.exchangeItemDetailMenu.handleClick(event, new ItemKey(current.getItemMeta().getLocalizedName()));
            }
        }
    }

    private ItemCategory parseCategory(final String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "farming" -> ItemCategory.FARMING;
            case "mining" -> ItemCategory.MINING;
            case "mob drops" -> ItemCategory.MOB_DROPS;
            case "building" -> ItemCategory.BUILDING;
            case "utility" -> ItemCategory.UTILITY;
            default -> null;
        };
    }
}
```

---

# 10. Update `/shop` command behavior

## File: `src/main/java/com/splatage/wild_economy/command/ShopOpenSubcommand.java`

Replace the empty stub with this version.

```java
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.gui.ShopMenuRouter;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopOpenSubcommand {

    private final ShopMenuRouter shopMenuRouter;

    public ShopOpenSubcommand(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        this.shopMenuRouter.openRoot(player);
        return true;
    }
}
```

## File: `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`

Replace the Commit 2D version with this Commit 3 version.

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

    public ShopCommand(
        final ShopOpenSubcommand openSubcommand,
        final ShopSellHandSubcommand sellHandSubcommand,
        final ShopSellAllSubcommand sellAllSubcommand
    ) {
        this.openSubcommand = Objects.requireNonNull(openSubcommand, "openSubcommand");
        this.sellHandSubcommand = Objects.requireNonNull(sellHandSubcommand, "sellHandSubcommand");
        this.sellAllSubcommand = Objects.requireNonNull(sellAllSubcommand, "sellAllSubcommand");
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
            default -> {
                sender.sendMessage("Unknown subcommand. Use /shop, /shop sellhand, or /shop sellall.");
                yield true;
            }
        };
    }
}
```

---

# 11. Update `ServiceRegistry.java`

Replace the Commit 2D version with this Commit 3 version.

## File: `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

```java
package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.command.ShopAdminCommand;
import com.splatage.wild_economy.command.ShopCommand;
import com.splatage.wild_economy.command.ShopOpenSubcommand;
import com.splatage.wild_economy.command.ShopSellAllSubcommand;
import com.splatage.wild_economy.command.ShopSellHandSubcommand;
import com.splatage.wild_economy.config.ConfigLoader;
import com.splatage.wild_economy.config.DatabaseConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.GlobalConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.economy.EconomyGateway;
import com.splatage.wild_economy.economy.VaultEconomyGateway;
import com.splatage.wild_economy.exchange.catalog.CatalogLoader;
import com.splatage.wild_economy.exchange.catalog.CatalogMergeService;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.WorthImporter;
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
import com.splatage.wild_economy.gui.ExchangeBrowseMenu;
import com.splatage.wild_economy.gui.ExchangeItemDetailMenu;
import com.splatage.wild_economy.gui.ExchangeRootMenu;
import com.splatage.wild_economy.gui.ShopMenuListener;
import com.splatage.wild_economy.gui.ShopMenuRouter;
import com.splatage.wild_economy.persistence.DatabaseProvider;
import com.splatage.wild_economy.persistence.MigrationManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ServiceRegistry {

    private final WildEconomyPlugin plugin;

    private GlobalConfig globalConfig;
    private DatabaseConfig databaseConfig;
    private WorthImportConfig worthImportConfig;
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
        this.worthImportConfig = configLoader.loadWorthImportConfig();
        this.exchangeItemsConfig = configLoader.loadExchangeItemsConfig();

        this.databaseProvider = new DatabaseProvider(this.databaseConfig);

        final SchemaVersionRepository schemaVersionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteSchemaVersionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL wiring not implemented yet");
        };

        final MigrationManager migrationManager = new MigrationManager(this.databaseProvider, schemaVersionRepository);
        migrationManager.migrate();

        this.exchangeStockRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeStockRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL stock repository not implemented yet");
        };

        this.exchangeTransactionRepository = switch (this.databaseProvider.dialect()) {
            case SQLITE -> new SqliteExchangeTransactionRepository(this.databaseProvider);
            case MYSQL -> throw new UnsupportedOperationException("MySQL transaction repository not implemented yet");
        };

        final WorthImporter worthImporter = new WorthImporter();
        final CatalogMergeService catalogMergeService = new CatalogMergeService();
        final CatalogLoader catalogLoader = new CatalogLoader(worthImporter, catalogMergeService);
        this.exchangeCatalog = catalogLoader.load(this.exchangeItemsConfig, this.worthImportConfig);

        final CanonicalItemRules canonicalItemRules = new CanonicalItemRules();
        this.itemNormalizer = new BukkitItemNormalizer(canonicalItemRules);
        this.itemValidationService = new ItemValidationServiceImpl(this.itemNormalizer, this.exchangeCatalog);

        this.economyGateway = this.resolveVaultEconomy();

        final StockStateResolver stockStateResolver = new StockStateResolver();
        this.stockService = new StockServiceImpl(this.exchangeStockRepository, this.exchangeCatalog, stockStateResolver);
        this.pricingService = new PricingServiceImpl(this.exchangeCatalog);
        this.transactionLogService = new TransactionLogServiceImpl(this.exchangeTransactionRepository);
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

        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(this.exchangeService);
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(this.exchangeService, itemDetailMenu);
        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(browseMenu);
        this.shopMenuRouter = new ShopMenuRouter(rootMenu);

        this.plugin.getServer().getPluginManager().registerEvents(
            new ShopMenuListener(rootMenu, browseMenu, itemDetailMenu),
            this.plugin
        );
    }

    public void registerCommands() {
        final PluginCommand shop = this.plugin.getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(
                new ShopOpenSubcommand(this.shopMenuRouter),
                new ShopSellHandSubcommand(this.exchangeService),
                new ShopSellAllSubcommand(this.exchangeService)
            ));
        }

        final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
        if (shopAdmin != null) {
            shopAdmin.setExecutor(new ShopAdminCommand(this.plugin));
        }
    }

    public void registerTasks() {
        // Turnover task wiring comes later.
    }

    public void shutdown() {
        // No pooled resources yet.
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }
        return new VaultEconomyGateway(registration.getProvider());
    }
}
```

---

# 12. Commit 3 acceptance criteria

After applying these files:

## `/shop`

* opens the root menu
* category buttons are clickable
* category opens a browse view

## browse view

* shows buy-enabled items in that category
* shows price, stock, and stock state
* clicking an item opens detail view

## item detail view

* shows item information
* buy buttons for 1, 8, and 64 work
* buy succeeds only when stock and balance allow
* player receives items
* eco is withdrawn
* stock decrements for `PLAYER_STOCKED` items
* transactions are logged

## safety

* no purchase without successful withdrawal
* no stock decrement without successful purchase
* no purchase when inventory is full

---

# 13. Known limitations after Commit 3

Still intentionally deferred:

* pagination state beyond simple next page behavior
* proper back navigation from detail to browse/root
* polished message/config localization for GUI actions
* async DB optimization in click flows
* MySQL runtime path
* turnover execution
* admin stock GUI or deep admin tooling

These are acceptable to defer.

---

# 14. Best next step after Commit 3

The strongest next step is **Commit 4: hardening and cleanup**.

That should likely include:

* back navigation and page/session tracking cleanup
* message/config cleanup
* inventory-full and edge-case handling polish
* turnover task implementation
* MySQL implementations
* actual reload support for runtime services/catalog

That would move the plugin from “working prototype slice” toward a stable v1 base.
