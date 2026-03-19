package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.UUID;

public record MenuSession(
    UUID playerId,
    ViewType viewType,
    ItemCategory currentCategory,
    int currentPage,
    ItemKey currentItemKey
) {
    public enum ViewType {
        ROOT,
        BROWSE,
        DETAIL
    }
}
