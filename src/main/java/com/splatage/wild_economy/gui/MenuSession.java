package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.UUID;

public record MenuSession(
    UUID playerId,
    ViewType viewType,
    ItemCategory currentCategory,
    GeneratedItemCategory currentGeneratedCategory,
    int currentPage,
    ItemKey currentItemKey,
    boolean viaSubcategory
) {
    public enum ViewType {
        ROOT,
        SUBCATEGORY,
        BROWSE,
        DETAIL
    }
}
