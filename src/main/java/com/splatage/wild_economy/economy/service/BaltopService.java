package com.splatage.wild_economy.economy.service;

import com.splatage.wild_economy.economy.model.BalanceRankEntry;
import java.util.List;

public interface BaltopService {
    List<BalanceRankEntry> getPage(int page, int pageSize);
    void invalidate();
}
