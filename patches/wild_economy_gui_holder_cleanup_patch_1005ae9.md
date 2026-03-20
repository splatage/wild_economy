# wild_economy GUI holder cleanup patch for commit `1005ae9`
This bundle folds the cleanup pass into the holder-based menu patch.
It removes the leftover session-store layer entirely.

## Delete these files

- `src/main/java/com/splatage/wild_economy/gui/MenuSession.java`
- `src/main/java/com/splatage/wild_economy/gui/MenuSessionStore.java`

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuHolder.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopMenuHolder implements InventoryHolder {

    public enum ViewType {
        ROOT,
        SUBCATEGORY,
        BROWSE,
        DETAIL
    }

    private final ViewType viewType;
    private final ItemCategory currentCategory;
    private final GeneratedItemCategory currentGeneratedCategory;
    private final int currentPage;
    private final ItemKey currentItemKey;
    private final boolean viaSubcategory;
    private Inventory inventory;

    private ShopMenuHolder(
        final ViewType viewType,
        final ItemCategory currentCategory,
        final GeneratedItemCategory currentGeneratedCategory,
        final int currentPage,
        final ItemKey currentItemKey,
        final boolean viaSubcategory
    ) {
        this.viewType = Objects.requireNonNull(viewType, "viewType");
        this.currentCategory = currentCategory;
        this.currentGeneratedCategory = currentGeneratedCategory;
        this.currentPage = currentPage;
        this.currentItemKey = currentItemKey;
        this.viaSubcategory = viaSubcategory;
    }

    public static ShopMenuHolder root() {
        return new ShopMenuHolder(ViewType.ROOT, null, null, 0, null, false);
    }

    public static ShopMenuHolder subcategory(final ItemCategory category) {
        return new ShopMenuHolder(
            ViewType.SUBCATEGORY,
            Objects.requireNonNull(category, "category"),
            null,
            0,
            null,
            false
        );
    }

    public static ShopMenuHolder browse(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        return new ShopMenuHolder(
            ViewType.BROWSE,
            Objects.requireNonNull(category, "category"),
            generatedCategory,
            page,
            null,
            viaSubcategory
        );
    }

    public static ShopMenuHolder detail(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final ItemKey itemKey,
        final boolean viaSubcategory
    ) {
        return new ShopMenuHolder(
            ViewType.DETAIL,
            category,
            generatedCategory,
            page,
            Objects.requireNonNull(itemKey, "itemKey"),
            viaSubcategory
        );
    }

    public Inventory createInventory(final int size, final String title) {
        final Inventory created = Bukkit.createInventory(this, size, title);
        this.inventory = created;
        return created;
    }

    @Override
    public Inventory getInventory() {
        if (this.inventory == null) {
            throw new IllegalStateException("Inventory has not been created for this holder yet");
        }
        return this.inventory;
    }

    public ViewType viewType() {
        return this.viewType;
    }

    public ItemCategory currentCategory() {
        return this.currentCategory;
    }

    public GeneratedItemCategory currentGeneratedCategory() {
        return this.currentGeneratedCategory;
    }

    public int currentPage() {
        return this.currentPage;
    }

    public ItemKey currentItemKey() {
        return this.currentItemKey;
    }

    public boolean viaSubcategory() {
        return this.viaSubcategory;
    }
}

```

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeRootMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeRootMenu {

    private final ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeRootMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player) {
        final ShopMenuHolder holder = ShopMenuHolder.root();
        final Inventory inventory = holder.createInventory(27, "Shop");

        inventory.setItem(10, this.button(Material.BREAD, ItemCategory.FARMING_AND_FOOD.displayName()));
        inventory.setItem(11, this.button(Material.IRON_PICKAXE, ItemCategory.MINING_AND_MINERALS.displayName()));
        inventory.setItem(12, this.button(Material.BONE, ItemCategory.MOB_DROPS.displayName()));
        inventory.setItem(13, this.button(Material.BRICKS, ItemCategory.BUILDING_MATERIALS.displayName()));
        inventory.setItem(14, this.button(Material.REDSTONE, ItemCategory.REDSTONE_AND_UTILITY.displayName()));
        inventory.setItem(15, this.button(Material.DIAMOND_SWORD, ItemCategory.COMBAT_AND_ADVENTURE.displayName()));
        inventory.setItem(16, this.button(Material.CHEST, ItemCategory.MISC.displayName()));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> this.openCategory(player, ItemCategory.FARMING_AND_FOOD);
            case 11 -> this.openCategory(player, ItemCategory.MINING_AND_MINERALS);
            case 12 -> this.openCategory(player, ItemCategory.MOB_DROPS);
            case 13 -> this.openCategory(player, ItemCategory.BUILDING_MATERIALS);
            case 14 -> this.openCategory(player, ItemCategory.REDSTONE_AND_UTILITY);
            case 15 -> this.openCategory(player, ItemCategory.COMBAT_AND_ADVENTURE);
            case 16 -> this.openCategory(player, ItemCategory.MISC);
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void openCategory(final Player player, final ItemCategory category) {
        final int visibleCount = this.exchangeService.countVisibleItems(category, null);
        final List<GeneratedItemCategory> subcategories = this.exchangeService.listVisibleSubcategories(category);

        if (visibleCount > 45 && subcategories.size() > 1) {
            this.shopMenuRouter.openSubcategory(player, category);
            return;
        }

        this.shopMenuRouter.openBrowse(player, category, null, 0, false);
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

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeSubcategoryMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeSubcategoryMenu {

    private static final int[] SUBCATEGORY_SLOTS = {11, 12, 13, 14, 15, 20, 21, 23, 24};

    private final ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeSubcategoryMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(final Player player, final ItemCategory category) {
        final ShopMenuHolder holder = ShopMenuHolder.subcategory(category);
        final Inventory inventory = holder.createInventory(27, "Shop - " + category.displayName() + " Types");

        inventory.setItem(10, this.button(Material.CHEST, "All"));

        final List<GeneratedItemCategory> subcategories = this.exchangeService.listVisibleSubcategories(category);
        for (int i = 0; i < subcategories.size() && i < SUBCATEGORY_SLOTS.length; i++) {
            final GeneratedItemCategory generatedItemCategory = subcategories.get(i);
            inventory.setItem(
                SUBCATEGORY_SLOTS[i],
                this.button(this.icon(generatedItemCategory), generatedItemCategory.displayName())
            );
        }

        inventory.setItem(18, this.button(Material.ARROW, "Back"));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final ItemCategory category) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot == 10) {
            this.shopMenuRouter.openBrowse(player, category, null, 0, true);
            return;
        }

        if (rawSlot == 18) {
            this.shopMenuRouter.openRoot(player);
            return;
        }

        if (rawSlot == 22) {
            player.closeInventory();
            return;
        }

        final List<GeneratedItemCategory> subcategories = this.exchangeService.listVisibleSubcategories(category);
        for (int i = 0; i < subcategories.size() && i < SUBCATEGORY_SLOTS.length; i++) {
            if (SUBCATEGORY_SLOTS[i] == rawSlot) {
                this.shopMenuRouter.openBrowse(player, category, subcategories.get(i), 0, true);
                return;
            }
        }
    }

    private Material icon(final GeneratedItemCategory generatedItemCategory) {
        return switch (generatedItemCategory) {
            case FARMING -> Material.WHEAT;
            case FOOD -> Material.BREAD;
            case ORES_AND_MINERALS -> Material.IRON_INGOT;
            case MOB_DROPS -> Material.BONE;
            case WOODS -> Material.OAK_LOG;
            case STONE -> Material.STONE;
            case REDSTONE -> Material.REDSTONE;
            case TOOLS -> Material.IRON_PICKAXE;
            case BREWING -> Material.BREWING_STAND;
            case TRANSPORT -> Material.MINECART;
            case COMBAT -> Material.DIAMOND_SWORD;
            case NETHER -> Material.NETHERRACK;
            case END -> Material.END_STONE;
            case DECORATION -> Material.PAINTING;
            case MISC -> Material.CHEST;
        };
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

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeBrowseMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.service.ExchangeCatalogView;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeBrowseMenu {

    private final ExchangeService exchangeService;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeBrowseMenu(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(
        final Player player,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        final ShopMenuHolder holder = ShopMenuHolder.browse(category, generatedCategory, page, viaSubcategory);
        final Inventory inventory = holder.createInventory(54, this.title(category, generatedCategory));
        final List<ExchangeCatalogView> entries = this.exchangeService.browseCategory(category, generatedCategory, page, 45);

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

    public void handleClick(
        final InventoryClickEvent event,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot >= 0 && slot < 45) {
            final ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return;
            }

            final ItemKey itemKey = this.toItemKey(clicked.getType());
            this.shopMenuRouter.openDetail(player, itemKey);
            return;
        }

        switch (slot) {
            case 45 -> {
                if (viaSubcategory) {
                    this.shopMenuRouter.openSubcategory(player, category);
                } else {
                    this.shopMenuRouter.openRoot(player);
                }
            }
            case 49 -> player.closeInventory();
            case 53 -> this.shopMenuRouter.openBrowse(player, category, generatedCategory, page + 1, viaSubcategory);
            default -> {
            }
        }
    }

    private String title(final ItemCategory category, final GeneratedItemCategory generatedCategory) {
        if (generatedCategory == null) {
            return "Shop - " + category.displayName();
        }
        return "Shop - " + category.displayName() + " / " + generatedCategory.displayName();
    }

    private ItemStack catalogItem(final ExchangeCatalogView view) {
        final Material material = this.resolveMaterial(view.itemKey());
        final ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        final ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(view.displayName());
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

    private ItemKey toItemKey(final Material material) {
        return new ItemKey("minecraft:" + material.name().toLowerCase());
    }
}

```

## File: `src/main/java/com/splatage/wild_economy/gui/ExchangeItemDetailMenu.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.service.ExchangeItemView;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeItemDetailMenu {

    private final ExchangeService exchangeService;
    private final PlatformExecutor platformExecutor;
    private ShopMenuRouter shopMenuRouter;

    public ExchangeItemDetailMenu(final ExchangeService exchangeService, final PlatformExecutor platformExecutor) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
    }

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public void open(
        final Player player,
        final ItemKey itemKey,
        final int amount,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        final ExchangeItemView view = this.exchangeService.getItemView(itemKey);
        final ShopMenuHolder holder = ShopMenuHolder.detail(
            category,
            generatedCategory,
            page,
            itemKey,
            viaSubcategory
        );
        final Inventory inventory = holder.createInventory(27, "Buy - " + view.displayName());

        inventory.setItem(11, this.detailItem(view, amount));
        inventory.setItem(13, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 1"));
        inventory.setItem(14, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 8"));
        inventory.setItem(15, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 64"));
        inventory.setItem(18, this.button(Material.ARROW, "Back"));
        inventory.setItem(22, this.button(Material.BARRIER, "Close"));

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final ShopMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final ItemKey itemKey = holder.currentItemKey();
        if (itemKey == null) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot == 18) {
            this.shopMenuRouter.goBack(player);
            return;
        }

        if (slot == 22) {
            player.closeInventory();
            return;
        }

        final int amount = switch (slot) {
            case 13 -> 1;
            case 14 -> 8;
            case 15 -> 64;
            default -> 0;
        };

        if (amount > 0) {
            this.platformExecutor.runOnPlayer(player, () -> {
                final BuyResult result = this.exchangeService.buy(player.getUniqueId(), itemKey, amount);
                player.sendMessage(result.message());

                if (result.success()) {
                    this.open(
                        player,
                        itemKey,
                        1,
                        holder.currentCategory(),
                        holder.currentGeneratedCategory(),
                        holder.currentPage(),
                        holder.viaSubcategory()
                    );
                }
            });
        }
    }

    private ItemStack detailItem(final ExchangeItemView view, final int amount) {
        final Material material = this.resolveMaterial(view.itemKey());
        final ItemStack stack = new ItemStack(
            material == null ? Material.BARRIER : material,
            Math.max(1, Math.min(amount, 64))
        );
        final ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(view.displayName());
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

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuListener.java`

```java
package com.splatage.wild_economy.gui;

import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ShopMenuListener implements Listener {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;

    public ShopMenuListener(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final ShopMenuHolder holder = ShopMenuRouter.getShopMenuHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        event.setCancelled(true);

        switch (holder.viewType()) {
            case ROOT -> this.exchangeRootMenu.handleClick(event);
            case SUBCATEGORY -> {
                if (holder.currentCategory() != null) {
                    this.exchangeSubcategoryMenu.handleClick(event, holder.currentCategory());
                }
            }
            case BROWSE -> {
                if (holder.currentCategory() != null) {
                    this.exchangeBrowseMenu.handleClick(
                        event,
                        holder.currentCategory(),
                        holder.currentGeneratedCategory(),
                        holder.currentPage(),
                        holder.viaSubcategory()
                    );
                }
            }
            case DETAIL -> this.exchangeItemDetailMenu.handleClick(event, holder);
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        final ShopMenuHolder holder = ShopMenuRouter.getShopMenuHolder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }

        final int topInventorySize = event.getView().getTopInventory().getSize();
        for (final int rawSlot : event.getRawSlots()) {
            if (rawSlot < topInventorySize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}

```

## File: `src/main/java/com/splatage/wild_economy/gui/ShopMenuRouter.java`

```java
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopMenuRouter {

    private final PlatformExecutor platformExecutor;
    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;

    public ShopMenuRouter(
        final PlatformExecutor platformExecutor,
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    public void openRoot(final Player player) {
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeRootMenu.open(player));
    }

    public void openSubcategory(final Player player, final ItemCategory category) {
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeSubcategoryMenu.open(player, category));
    }

    public void openBrowse(
        final Player player,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.exchangeBrowseMenu.open(player, category, generatedCategory, page, viaSubcategory)
        );
    }

    public void openDetail(final Player player, final ItemKey itemKey) {
        final ShopMenuHolder previous = this.currentHolder(player);
        final ItemCategory category = previous == null ? null : previous.currentCategory();
        final GeneratedItemCategory generatedCategory =
            previous == null ? null : previous.currentGeneratedCategory();
        final int page = previous == null ? 0 : previous.currentPage();
        final boolean viaSubcategory = previous != null && previous.viaSubcategory();

        this.platformExecutor.runOnPlayer(
            player,
            () -> this.exchangeItemDetailMenu.open(
                player,
                itemKey,
                1,
                category,
                generatedCategory,
                page,
                viaSubcategory
            )
        );
    }

    public void goBack(final Player player) {
        final ShopMenuHolder holder = this.currentHolder(player);
        if (holder == null) {
            this.openRoot(player);
            return;
        }

        switch (holder.viewType()) {
            case ROOT -> this.openRoot(player);
            case SUBCATEGORY -> this.openRoot(player);
            case BROWSE -> {
                if (holder.viaSubcategory() && holder.currentCategory() != null) {
                    this.openSubcategory(player, holder.currentCategory());
                } else {
                    this.openRoot(player);
                }
            }
            case DETAIL -> {
                if (holder.currentCategory() == null) {
                    this.openRoot(player);
                } else {
                    this.openBrowse(
                        player,
                        holder.currentCategory(),
                        holder.currentGeneratedCategory(),
                        holder.currentPage(),
                        holder.viaSubcategory()
                    );
                }
            }
        }
    }

    public void closeAllShopViews() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (this.currentHolder(player) == null) {
                continue;
            }
            this.platformExecutor.runOnPlayer(player, player::closeInventory);
        }
    }

    private ShopMenuHolder currentHolder(final Player player) {
        return getShopMenuHolder(player.getOpenInventory().getTopInventory());
    }

    public static ShopMenuHolder getShopMenuHolder(final Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        final InventoryHolder holder = inventory.getHolder();
        if (holder instanceof ShopMenuHolder shopMenuHolder) {
            return shopMenuHolder;
        }

        return null;
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
import org.bukkit.event.HandlerList;
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
    private ShopMenuListener shopMenuListener;
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
            this.transactionLogService,
            this.globalConfig
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
            rootMenu,
            subcategoryMenu,
            browseMenu,
            itemDetailMenu
        );

        rootMenu.setShopMenuRouter(this.shopMenuRouter);
        subcategoryMenu.setShopMenuRouter(this.shopMenuRouter);
        browseMenu.setShopMenuRouter(this.shopMenuRouter);
        itemDetailMenu.setShopMenuRouter(this.shopMenuRouter);

        this.shopMenuListener = new ShopMenuListener(
            rootMenu,
            subcategoryMenu,
            browseMenu,
            itemDetailMenu
        );
        this.plugin.getServer().getPluginManager().registerEvents(this.shopMenuListener, this.plugin);
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
            shop.setExecutor(new ShopCommand(openSubcommand, sellHandSubcommand, sellAllSubcommand, sellContainerSubcommand));
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
        if (this.shopMenuListener != null) {
            HandlerList.unregisterAll(this.shopMenuListener);
            this.shopMenuListener = null;
        }

        if (this.shopMenuRouter != null) {
            this.shopMenuRouter.closeAllShopViews();
            this.shopMenuRouter = null;
        }

        this.platformExecutor.cancelPluginTasks();

        if (this.transactionLogService != null) {
            this.transactionLogService.shutdown();
            this.transactionLogService = null;
        }

        if (this.stockService != null) {
            this.stockService.shutdown();
            this.stockService = null;
        }

        if (this.databaseProvider != null) {
            this.databaseProvider.close();
            this.databaseProvider = null;
        }

        this.exchangeService = null;
        this.exchangeBuyService = null;
        this.exchangeSellService = null;
        this.exchangeBrowseService = null;
        this.stockTurnoverService = null;
        this.foliaContainerSellCoordinator = null;
    }

    private EconomyGateway resolveVaultEconomy() {
        final RegisteredServiceProvider<Economy> registration = this.plugin.getServer()
            .getServicesManager()
            .getRegistration(Economy.class);

        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }

        return new VaultEconomyGateway(registration.getProvider());
    }
}

```

