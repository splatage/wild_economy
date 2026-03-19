package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class ShopMenuRouter {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeSubcategoryMenu exchangeSubcategoryMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final Map<UUID, MenuSession> sessions = new HashMap<>();

    public ShopMenuRouter(
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

    public void openRoot(final Player player) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.ROOT,
            null,
            null,
            0,
            null,
            false
        ));
        this.exchangeRootMenu.open(player);
    }

    public void openSubcategory(final Player player, final ItemCategory category) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.SUBCATEGORY,
            category,
            null,
            0,
            null,
            false
        ));
        this.exchangeSubcategoryMenu.open(player, category);
    }

    public void openBrowse(
        final Player player,
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final boolean viaSubcategory
    ) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.BROWSE,
            category,
            generatedCategory,
            page,
            null,
            viaSubcategory
        ));
        this.exchangeBrowseMenu.open(player, category, generatedCategory, page, viaSubcategory);
    }

    public void openDetail(final Player player, final ItemKey itemKey) {
        final MenuSession previous = this.sessions.get(player.getUniqueId());
        final ItemCategory category = previous == null ? null : previous.currentCategory();
        final GeneratedItemCategory generatedCategory = previous == null ? null : previous.currentGeneratedCategory();
        final int page = previous == null ? 0 : previous.currentPage();
        final boolean viaSubcategory = previous != null && previous.viaSubcategory();

        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.DETAIL,
            category,
            generatedCategory,
            page,
            itemKey,
            viaSubcategory
        ));
        this.exchangeItemDetailMenu.open(player, itemKey, 1);
    }

    public void goBack(final Player player) {
        final MenuSession session = this.sessions.get(player.getUniqueId());
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
        return this.sessions.get(playerId);
    }

    public void clearSession(final UUID playerId) {
        this.sessions.remove(playerId);
    }
}
