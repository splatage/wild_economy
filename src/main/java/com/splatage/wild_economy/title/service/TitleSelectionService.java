package com.splatage.wild_economy.title.service;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

public interface TitleSelectionService {
    Optional<String> getSelectedTitleKey(Player player);
    Optional<String> getSelectedTitleKey(UUID playerId);
    void setSelectedTitleKey(Player player, String titleKey);
    void clearSelectedTitleKey(Player player);
}
