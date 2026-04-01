package com.splatage.wild_economy.title.service;

import com.splatage.wild_economy.title.model.PlayerTitleSelection;
import java.util.UUID;
import org.bukkit.entity.Player;

public interface TitleSelectionService {
    PlayerTitleSelection getSelection(Player player);
    void setSelection(Player player, PlayerTitleSelection selection);
    void clearSelection(Player player);
    default PlayerTitleSelection getSelection(final UUID playerId) {
        throw new UnsupportedOperationException("Offline selection lookup is not yet implemented for player " + playerId);
    }
}
