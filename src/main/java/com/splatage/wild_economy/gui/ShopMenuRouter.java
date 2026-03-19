package com.splatage.wild_economy.gui;

public final class ShopMenuRouter {
}
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
