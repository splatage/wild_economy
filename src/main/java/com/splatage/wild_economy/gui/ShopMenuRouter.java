package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ShopMenuRouter {

    private final PlatformExecutor platformExecutor;
    private final MenuSessionStore menuSessionStore;
    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;

    public ShopMenuRouter(
        final PlatformExecutor platformExecutor,
        final MenuSessionStore menuSessionStore,
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeSubcategoryMenu exchangeSubcategoryMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.menuSessionStore = Objects.requireNonNull(menuSessionStore, "menuSessionStore");
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeSubcategoryMenu = Objects.requireNonNull(exchangeSubcategoryMenu, "exchangeSubcategoryMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    public void openRoot(final Player player) {
        this.menuSessionStore.put(new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.ROOT,
            null,
            null,
            0,
            null,
            false
        ));
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeRootMenu.open(player));
    }

    public void openSubcategory(final Player player, final ItemCategory category) {
        this.menuSessionStore.put(new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.SUBCATEGORY,
            category,
            null,
            0,
            null,
            false
        ));
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeSubcategoryMenu.open(player, category));
    }

    public void openBrowse(
        final Player player,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        this.menuSessionStore.put(new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.BROWSE,
            category,
            generatedCategory,
            page,
            null,
            viaSubcategory
        ));
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.exchangeBrowseMenu.open(player, category, generatedCategory, page, viaSubcategory)
        );
    }

    public void openDetail(final Player player, final ItemKey itemKey) {
        final MenuSession previous = this.menuSessionStore.get(player.getUniqueId());
        final ItemCategory category = previous == null ? null : previous.currentCategory();
        final GeneratedItemCategory generatedCategory = previous == null ? null : previous.currentGeneratedCategory();
        final int page = previous == null ? 0 : previous.currentPage();
        final boolean viaSubcategory = previous != null && previous.viaSubcategory();

        this.menuSessionStore.put(new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.DETAIL,
            category,
            generatedCategory,
            page,
            itemKey,
            viaSubcategory
        ));
        this.platformExecutor.runOnPlayer(player, () -> this.exchangeItemDetailMenu.open(player, itemKey, 1));
    }

    public void goBack(final Player player) {
        final MenuSession session = this.menuSessionStore.get(player.getUniqueId());
        if (session == null) {
            this.openRoot(player);
            return;
        }

        switch (session.viewType()) {
            case ROOT -> this.openRoot(player);
            case SUBCATEGORY -> this.openRoot(player);
            case BROWSE -> {
                if (session.viaSubcategory() && session.currentCategory() != null) {
                    this.openSubcategory(player, session.currentCategory());
                } else {
                    this.openRoot(player);
                }
            }
            case DETAIL -> {
                if (session.currentCategory() == null) {
                    this.openRoot(player);
                } else {
                    this.openBrowse(
                        player,
                        session.currentCategory(),
                        session.currentGeneratedCategory(),
                        session.currentPage(),
                        session.viaSubcategory()
                    );
                }
            }
        }
    }

    public MenuSession getSession(final UUID playerId) {
        return this.menuSessionStore.get(playerId);
    }

    public void clearSession(final UUID playerId) {
        this.menuSessionStore.remove(playerId);
    }

    public void closeAllShopViews() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            this.clearSession(player.getUniqueId());

            final String title = player.getOpenInventory().getTitle();
            if (!isShopViewTitle(title)) {
                continue;
            }

            this.platformExecutor.runOnPlayer(player, player::closeInventory);
        }
    }

    public static boolean isShopViewTitle(final String title) {
        return title != null
            && (title.equals("Shop") || title.startsWith("Shop - ") || title.startsWith("Buy - "));
    }
}
