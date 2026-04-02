package com.splatage.wild_economy.title.service;

import org.bukkit.entity.Player;

public interface TitleTextResolver {
    String resolve(Player player, String rawText);
}
