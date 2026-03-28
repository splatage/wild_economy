package com.splatage.wild_economy.economy.service;

import com.splatage.wild_economy.economy.model.BalanceRankEntry;
import java.util.List;
import java.util.UUID;

public interface BaltopService {
    List<BalanceRankEntry> getPage(int page, int pageSize);
    int getRank(UUID playerId);
    void invalidate();
}
