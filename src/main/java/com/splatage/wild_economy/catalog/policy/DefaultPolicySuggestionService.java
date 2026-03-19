package com.splatage.wild_economy.catalog.policy;

import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.util.Set;

public final class DefaultPolicySuggestionService implements PolicySuggestionService {

    private static final Set<String> ALWAYS_AVAILABLE = Set.of(
        "sand",
        "red_sand",
        "gravel",
        "ice",
        "packed_ice",
        "blue_ice",
        "oak_log",
        "spruce_log",
        "birch_log",
        "jungle_log",
        "acacia_log",
        "dark_oak_log",
        "mangrove_log",
        "cherry_log",
        "crimson_stem",
        "warped_stem"
    );

    private static final Set<String> HARD_DISABLED_EXACT = Set.of(
        "air",
        "knowledge_book",
        "command_block",
        "chain_command_block",
        "repeating_command_block",
        "command_block_minecart",
        "jigsaw",
        "structure_block",
        "structure_void",
        "light",
        "barrier",
        "debug_stick",
        "written_book"
    );

    private final int maxAutoInclusionDepth;

    public DefaultPolicySuggestionService() {
        this(1);
    }

    public DefaultPolicySuggestionService(final int maxAutoInclusionDepth) {
        this.maxAutoInclusionDepth = maxAutoInclusionDepth;
    }

    @Override
    public CatalogPolicy suggest(
        final ItemFacts facts,
        final CatalogCategory category,
        final DerivedItemResult derivation
    ) {
        final String key = facts.key();

        if (isHardDisabled(key)) {
            return CatalogPolicy.DISABLED;
        }
        if (!derivation.resolved()) {
            return CatalogPolicy.DISABLED;
        }
        if (isDepthLimited(derivation)) {
            return CatalogPolicy.DISABLED;
        }
        if (ALWAYS_AVAILABLE.contains(key)) {
            return CatalogPolicy.ALWAYS_AVAILABLE;
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
            return "always-available allowlist";
        }
        if (policy == CatalogPolicy.EXCHANGE) {
            return switch (derivation.reason()) {
                case ROOT_ANCHOR -> "root-anchor";
                case DERIVED_FROM_ROOT -> "derived-from-root";
                default -> "policy override required";
            };
        }
        if (policy == CatalogPolicy.SELL_ONLY) {
            return "sell-only fallback";
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
        if (isHardDisabled(facts.key())) {
            return "hard-disabled non-standard or admin item";
        }
        if (isDepthLimited(derivation)) {
            return "depth-limit";
        }
        return switch (derivation.reason()) {
            case ALL_PATHS_BLOCKED -> "all-paths-blocked";
            case NO_RECIPE_AND_NO_ROOT -> "no-recipe-and-no-root";
            case CYCLE_DETECTED -> "cycle-detected";
            case HARD_DISABLED -> "hard-disabled";
            case ROOT_ANCHOR, DERIVED_FROM_ROOT, DEPTH_LIMIT -> "disabled by policy rules";
        };
    }

    private boolean isHardDisabled(final String key) {
        return HARD_DISABLED_EXACT.contains(key) || key.endsWith("_spawn_egg");
    }

    private boolean isDepthLimited(final DerivedItemResult derivation) {
        return derivation.resolved()
            && derivation.derivationDepth() != null
            && derivation.derivationDepth() > this.maxAutoInclusionDepth;
    }
}
