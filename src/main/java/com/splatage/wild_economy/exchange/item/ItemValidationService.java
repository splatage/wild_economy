package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import org.bukkit.inventory.ItemStack;

public interface ItemValidationService {
    ValidationResult validateForSell(ItemStack itemStack);
    ValidationResult validateForBuy(ItemKey itemKey);
}
