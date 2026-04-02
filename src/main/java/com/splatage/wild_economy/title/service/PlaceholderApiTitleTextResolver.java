package com.splatage.wild_economy.title.service;

import java.util.Objects;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PlaceholderApiTitleTextResolver implements TitleTextResolver {

    private final Plugin plugin;

    public PlaceholderApiTitleTextResolver(final Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String resolve(final Player player, final String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String resolved = rawText;
        if (player != null && this.plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders(player, resolved);
        }
        return ChatColor.translateAlternateColorCodes('&', resolved == null ? "" : resolved).trim();
    }
}
