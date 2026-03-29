package com.splatage.wild_economy.store.action;

import com.splatage.wild_economy.store.model.StoreProduct;
import org.bukkit.entity.Player;

public interface ProductActionExecutor {
    StoreActionExecutionResult execute(Player player, StoreProduct product);
}
