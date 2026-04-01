package com.splatage.wild_economy.title.service;

import com.splatage.wild_economy.title.model.ResolvedTitle;
import java.util.UUID;
import org.bukkit.entity.Player;

public interface ResolvedTitleService {
    ResolvedTitle resolve(Player player);
    ResolvedTitle getCached(UUID playerId);
    void invalidate(UUID playerId);
    void invalidateAll();
}
