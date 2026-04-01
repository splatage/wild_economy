package com.splatage.wild_economy.title.listener;

import com.splatage.wild_economy.title.service.ResolvedTitleService;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TitleSessionListener implements Listener {

    private final ResolvedTitleService resolvedTitleService;

    public TitleSessionListener(final ResolvedTitleService resolvedTitleService) {
        this.resolvedTitleService = Objects.requireNonNull(resolvedTitleService, "resolvedTitleService");
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        this.resolvedTitleService.warm(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.resolvedTitleService.invalidate(event.getPlayer().getUniqueId());
    }
}
