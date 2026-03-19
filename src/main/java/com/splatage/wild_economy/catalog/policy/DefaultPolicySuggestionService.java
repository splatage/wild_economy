package com.splatage.wild_economy.catalog.policy;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.math.BigDecimal;
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

    @Override
    public CatalogPolicy suggest(final ItemFacts facts, final CatalogCategory category, final BigDecimal basePrice) {
        final String key = facts.key();

        if (isHardDisabled(key)) {
            return CatalogPolicy.DISABLED;
        }
        if (ALWAYS_AVAILABLE.contains(key)) {
            return CatalogPolicy.ALWAYS_AVAILABLE;
        }
        if (facts.hasWorthEntry()) {
            return CatalogPolicy.EXCHANGE;
        }
        return CatalogPolicy.DISABLED;
    }

    @Override
    public String includeReason(
        final ItemFacts facts,
        final CatalogCategory category,
        final BigDecimal basePrice,
        final CatalogPolicy policy
    ) {
        return switch (policy) {
            case ALWAYS_AVAILABLE -> "always-available allowlist";
            case EXCHANGE -> facts.hasWorthEntry()
                ? "worth-present default exchange"
                : "policy override required";
            case SELL_ONLY -> "sell-only fallback";
            case DISABLED -> "";
        };
    }

    @Override
    public String excludeReason(
        final ItemFacts facts,
        final CatalogCategory category,
        final BigDecimal basePrice,
        final CatalogPolicy policy
    ) {
        if (policy != CatalogPolicy.DISABLED) {
            return "";
        }
        if (isHardDisabled(facts.key())) {
            return "hard-disabled non-standard or admin item";
        }
        if (!facts.hasWorthEntry()) {
            return "missing worth entry";
        }
        return "disabled by policy rules";
    }

    private boolean isHardDisabled(final String key) {
        return HARD_DISABLED_EXACT.contains(key) || key.endsWith("_spawn_egg");
    }
}
