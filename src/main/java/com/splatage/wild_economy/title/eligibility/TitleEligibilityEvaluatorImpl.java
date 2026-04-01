package com.splatage.wild_economy.title.eligibility;

import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.store.eligibility.StoreRequirementGateService;
import com.splatage.wild_economy.title.model.TitleOption;
import java.util.Objects;
import org.bukkit.entity.Player;

public final class TitleEligibilityEvaluatorImpl implements TitleEligibilityEvaluator {

    private final StoreRequirementGateService requirementGateService;

    public TitleEligibilityEvaluatorImpl(final StoreRequirementGateService requirementGateService) {
        this.requirementGateService = Objects.requireNonNull(requirementGateService, "requirementGateService");
    }

    @Override
    public StoreEligibilityResult evaluate(final Player player, final TitleOption option) {
        return this.requirementGateService.evaluate(
                player,
                option.requirements(),
                option.visibilityWhenUnmet(),
                option.lockedMessage()
        );
    }
}
