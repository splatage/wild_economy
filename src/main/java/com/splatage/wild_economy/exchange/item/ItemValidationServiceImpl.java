package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import org.bukkit.inventory.ItemStack;

public final class ItemValidationServiceImpl implements ItemValidationService {

    @Override
    public ValidationResult validateForSell(final ItemStack itemStack) {
        return new ValidationResult(false, null, RejectionReason.INTERNAL_ERROR, "Not implemented");
    }

    @Override
    public ValidationResult validateForBuy(final ItemKey itemKey) {
        return new ValidationResult(false, itemKey, RejectionReason.INTERNAL_ERROR, "Not implemented");
    }
}
