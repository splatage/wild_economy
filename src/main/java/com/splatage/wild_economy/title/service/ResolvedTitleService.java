package com.splatage.wild_economy.title.service;

import com.splatage.wild_economy.title.model.ResolvedTitle;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public interface ResolvedTitleService {
    Optional<ResolvedTitle> getResolvedTitle(OfflinePlayer player);
    void warm(Player player);
    void invalidate(UUID playerId);
}
