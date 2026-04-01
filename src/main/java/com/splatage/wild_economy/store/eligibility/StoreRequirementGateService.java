package com.splatage.wild_economy.store.eligibility;

import com.splatage.wild_economy.store.model.StoreRequirement;
import com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet;
import java.util.List;
import org.bukkit.entity.Player;

public interface StoreRequirementGateService {
    StoreEligibilityResult evaluate(
            Player player,
            List<StoreRequirement> requirements,
            StoreVisibilityWhenUnmet visibilityWhenUnmet,
            String lockedMessage
    );
}
