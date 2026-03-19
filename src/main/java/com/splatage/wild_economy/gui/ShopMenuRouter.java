package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class ShopMenuRouter {

    private final ExchangeRootMenu exchangeRootMenu;
    private final ExchangeBrowseMenu exchangeBrowseMenu;
    private final ExchangeItemDetailMenu exchangeItemDetailMenu;
    private final Map<UUID, MenuSession> sessions = new HashMap<>();

    public ShopMenuRouter(
        final ExchangeRootMenu exchangeRootMenu,
        final ExchangeBrowseMenu exchangeBrowseMenu,
        final ExchangeItemDetailMenu exchangeItemDetailMenu
    ) {
        this.exchangeRootMenu = Objects.requireNonNull(exchangeRootMenu, "exchangeRootMenu");
        this.exchangeBrowseMenu = Objects.requireNonNull(exchangeBrowseMenu, "exchangeBrowseMenu");
        this.exchangeItemDetailMenu = Objects.requireNonNull(exchangeItemDetailMenu, "exchangeItemDetailMenu");
    }

    public void openRoot(final Player player) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.ROOT,
            null,
            0,
            null
        ));
        this.exchangeRootMenu.open(player);
    }

    public void openBrowse(final Player player, final ItemCategory category, final int page) {
        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.BROWSE,
            category,
            page,
            null
        ));
        this.exchangeBrowseMenu.open(player, category, page);
    }

    public void openDetail(final Player player, final ItemKey itemKey) {
        final MenuSession previous = this.sessions.get(player.getUniqueId());
        final ItemCategory category = previous == null ? null : previous.currentCategory();
        final int page = previous == null ? 0 : previous.currentPage();

        this.sessions.put(player.getUniqueId(), new MenuSession(
            player.getUniqueId(),
            MenuSession.ViewType.DETAIL,
            category,
            page,
            itemKey
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
            case BROWSE -> this.openRoot(player);
            case DETAIL -> {
                if (session.currentCategory() == null) {
                    this.openRoot(player);
                } else {
                    this.openBrowse(player, session.currentCategory(), session.currentPage());
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
