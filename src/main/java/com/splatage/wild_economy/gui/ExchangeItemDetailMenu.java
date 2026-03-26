package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.service.ExchangeItemView;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ExchangeItemDetailMenu {

    private static final int MONEY_SCALE = 2;
    private static final long QUOTE_MAX_AGE_MILLIS = 30_000L;

    private final ExchangeService exchangeService;
    private final PlatformExecutor platformExecutor;
    private final PlayerInfoItemFactory playerInfoItemFactory;

    private ShopMenuRouter shopMenuRouter;

    public ExchangeItemDetailMenu(
        final ExchangeService exchangeService,
        final PlatformExecutor platformExecutor,
        final PlayerInfoItemFactory playerInfoItemFactory
    ) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.playerInfoItemFactory = Objects.requireNonNull(playerInfoItemFactory, "playerInfoItemFactory");
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
        final BigDecimal quotedUnitPrice = this.normalizedMoney(view.buyPrice());
        final long quotedAtMillis = System.currentTimeMillis();

        final ShopMenuHolder holder = ShopMenuHolder.detail(
            category,
            generatedCategory,
            page,
            itemKey,
            viaSubcategory,
            quotedUnitPrice,
            quotedAtMillis
        );

        final Inventory inventory = holder.createInventory(27, "Buy - " + view.displayName());
        inventory.setItem(21, this.playerInfoItemFactory.create(player));
        inventory.setItem(11, this.detailItem(view, amount));
        inventory.setItem(13, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 1", this.quoteLore(quotedUnitPrice, 1)));
        inventory.setItem(14, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 8", this.quoteLore(quotedUnitPrice, 8)));
        inventory.setItem(15, this.button(Material.GREEN_STAINED_GLASS_PANE, "Buy 64", this.quoteLore(quotedUnitPrice, 64)));
        inventory.setItem(18, this.button(Material.ARROW, "Back", List.of("Return to the previous menu")));
        inventory.setItem(22, this.button(Material.BARRIER, "Close", List.of("Close the shop")));

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

        if (amount <= 0) {
            return;
        }

        if (this.quoteExpired(holder)) {
            player.sendMessage("That quote has expired. The detail view has been refreshed with the latest price.");
            this.open(
                player,
                itemKey,
                1,
                holder.currentCategory(),
                holder.currentGeneratedCategory(),
                holder.currentPage(),
                holder.viaSubcategory()
            );
            return;
        }

        this.platformExecutor.runOnPlayer(player, () -> {
            final BuyResult result = this.exchangeService.buyQuoted(
                player.getUniqueId(),
                itemKey,
                amount,
                holder.quotedUnitPrice()
            );

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
                "Unit price: " + this.normalizedMoney(view.buyPrice()),
                "Stock: " + view.stockCount(),
                "State: " + view.stockState().name(),
                "This menu view locks the shown buy price for its buttons.",
                "Quote lifetime: 30 seconds."
            ));
            stack.setItemMeta(meta);
        }

        return stack;
    }

    private List<String> quoteLore(final BigDecimal quotedUnitPrice, final int amount) {
        return List.of(
            "Quoted total: " + this.totalPrice(quotedUnitPrice, amount),
            "This quoted price is honored while the menu quote remains fresh.",
            "Quote lifetime: 30 seconds."
        );
    }

    private ItemStack button(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private BigDecimal totalPrice(final BigDecimal unitPrice, final int amount) {
        return unitPrice.multiply(BigDecimal.valueOf(amount)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizedMoney(final BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private boolean quoteExpired(final ShopMenuHolder holder) {
        final long quotedAtMillis = holder.quotedAtMillis();
        if (quotedAtMillis <= 0L) {
            return true;
        }
        return System.currentTimeMillis() - quotedAtMillis > QUOTE_MAX_AGE_MILLIS;
    }

    private Material resolveMaterial(final ItemKey itemKey) {
        return Material.matchMaterial(itemKey.value().replace("minecraft:", "").toUpperCase());
    }
}
