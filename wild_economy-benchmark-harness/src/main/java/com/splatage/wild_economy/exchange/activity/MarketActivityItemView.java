package com.splatage.wild_economy.exchange.activity;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;

public record MarketActivityItemView(
    ItemKey itemKey,
    String displayName,
    long eventEpochSecond,
    BigDecimal totalValue,
    int amount
) {}
