package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.util.List;
import java.util.Objects;

public record AdminCatalogPolicyRule(
    String id,
    List<String> itemKeys,
    List<String> itemKeyPatterns,
    List<CatalogCategory> categories,
    List<DerivationReason> derivationReasons,
    Integer minDerivationDepth,
    Integer maxDerivationDepth,
    Boolean rootValuePresent,
    CatalogPolicy policy,
    String stockProfile,
    String ecoEnvelope,
    String note
) {

    public AdminCatalogPolicyRule {
        Objects.requireNonNull(id, "id");
        itemKeys = AdminCatalogItemKeys.canonicalizeAll(itemKeys);
        itemKeyPatterns = AdminCatalogItemKeys.canonicalizeAll(itemKeyPatterns);
        categories = List.copyOf(categories);
        derivationReasons = List.copyOf(derivationReasons);
    }

    public boolean matches(
        final ItemFacts facts,
        final CatalogCategory category,
        final DerivedItemResult derivation
    ) {
        final String itemKey = AdminCatalogItemKeys.canonicalize(facts.key());

        if (!this.itemKeys.isEmpty() && !this.itemKeys.contains(itemKey)) {
            return false;
        }

        if (!this.itemKeyPatterns.isEmpty()) {
            boolean matchedPattern = false;
            for (final String pattern : this.itemKeyPatterns) {
                if (wildcardMatches(pattern, itemKey)) {
                    matchedPattern = true;
                    break;
                }
            }
            if (!matchedPattern) {
                return false;
            }
        }

        if (!this.categories.isEmpty() && !this.categories.contains(category)) {
            return false;
        }

        if (!this.derivationReasons.isEmpty() && !this.derivationReasons.contains(derivation.reason())) {
            return false;
        }

        if (this.rootValuePresent != null && this.rootValuePresent.booleanValue() != derivation.rootValuePresent()) {
            return false;
        }

        if (this.minDerivationDepth != null) {
            final int depth = derivation.derivationDepth() == null ? -1 : derivation.derivationDepth().intValue();
            if (depth < this.minDerivationDepth.intValue()) {
                return false;
            }
        }

        if (this.maxDerivationDepth != null) {
            final int depth = derivation.derivationDepth() == null ? Integer.MAX_VALUE : derivation.derivationDepth().intValue();
            if (depth > this.maxDerivationDepth.intValue()) {
                return false;
            }
        }

        return true;
    }

    public boolean hasMatchCriteria() {
        return !this.itemKeys.isEmpty()
            || !this.itemKeyPatterns.isEmpty()
            || !this.categories.isEmpty()
            || !this.derivationReasons.isEmpty()
            || this.minDerivationDepth != null
            || this.maxDerivationDepth != null
            || this.rootValuePresent != null;
    }

    private static boolean wildcardMatches(final String wildcardPattern, final String value) {
        final StringBuilder regex = new StringBuilder("^");
        for (final char c : wildcardPattern.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '$', '^', '+', '|' -> regex.append('\\').append(c);
                default -> regex.append(c);
            }
        }
        regex.append('$');
        return value.matches(regex.toString());
    }
}

