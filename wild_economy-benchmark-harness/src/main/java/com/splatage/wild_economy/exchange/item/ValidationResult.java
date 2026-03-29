package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;

public record ValidationResult(
    boolean valid,
    ItemKey itemKey,
    RejectionReason rejectionReason,
    String detail
) {}
