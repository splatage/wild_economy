package com.splatage.wild_economy.catalog.policy;

import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;

public final class DefaultPolicySuggestionService implements PolicySuggestionService {

    public DefaultPolicySuggestionService() {
    }

    public DefaultPolicySuggestionService(final int ignoredMaxAutoInclusionDepth) {
    }

    @Override
    public CatalogPolicy suggest(
        final ItemFacts facts,
        final CatalogCategory category,
        final DerivedItemResult derivation
    ) {
        if (CatalogSafetyExclusions.isHardDisabled(facts.key())) {
            return CatalogPolicy.DISABLED;
        }
        if (!derivation.resolved()) {
            return CatalogPolicy.DISABLED;
        }
        return CatalogPolicy.EXCHANGE;
    }

    @Override
    public String includeReason(
        final ItemFacts facts,
        final CatalogCategory category,
        final DerivedItemResult derivation,
        final CatalogPolicy policy
    ) {
        if (policy == CatalogPolicy.ALWAYS_AVAILABLE) {
            return "policy-profile override";
        }
        if (policy == CatalogPolicy.EXCHANGE) {
            return switch (derivation.reason()) {
                case ROOT_ANCHOR -> "root-anchor";
                case DERIVED_FROM_ROOT -> "derived-from-root";
                default -> "fallback-generated";
            };
        }
        if (policy == CatalogPolicy.SELL_ONLY) {
            return "policy override";
        }
        return "";
    }

    @Override
    public String excludeReason(
        final ItemFacts facts,
        final CatalogCategory category,
        final DerivedItemResult derivation,
        final CatalogPolicy policy
    ) {
        if (policy != CatalogPolicy.DISABLED) {
            return "";
        }
        if (CatalogSafetyExclusions.isHardDisabled(facts.key())) {
            return "hard-disabled non-standard or admin item";
        }
        return switch (derivation.reason()) {
            case ALL_PATHS_BLOCKED -> "all-paths-blocked";
            case NO_RECIPE_AND_NO_ROOT -> "no-recipe-and-no-root";
            case CYCLE_DETECTED -> "cycle-detected";
            case HARD_DISABLED -> "hard-disabled";
            case ROOT_ANCHOR, DERIVED_FROM_ROOT, DEPTH_LIMIT -> "disabled by policy rules";
        };
    }
}
