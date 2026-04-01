package com.splatage.wild_economy.title.eligibility;

import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.title.model.TitleOption;
import org.bukkit.entity.Player;

public interface TitleEligibilityEvaluator {
    StoreEligibilityResult evaluate(Player player, TitleOption option);
}
