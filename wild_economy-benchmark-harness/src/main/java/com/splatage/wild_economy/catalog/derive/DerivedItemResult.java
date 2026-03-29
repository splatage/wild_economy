package com.splatage.wild_economy.catalog.derive;

import java.math.BigDecimal;

public record DerivedItemResult(
    boolean resolved,
    boolean rootValuePresent,
    BigDecimal rootValue,
    Integer derivationDepth,
    BigDecimal derivedValue,
    DerivationReason reason
) {

    public static DerivedItemResult rootAnchor(final BigDecimal rootValue) {
        return new DerivedItemResult(
            true,
            true,
            rootValue,
            0,
            rootValue,
            DerivationReason.ROOT_ANCHOR
        );
    }

    public static DerivedItemResult derived(
        final int derivationDepth,
        final BigDecimal derivedValue
    ) {
        return new DerivedItemResult(
            true,
            false,
            null,
            derivationDepth,
            derivedValue,
            DerivationReason.DERIVED_FROM_ROOT
        );
    }

    public static DerivedItemResult blocked(
        final Integer derivationDepth,
        final BigDecimal derivedValue,
        final DerivationReason reason
    ) {
        return new DerivedItemResult(
            false,
            false,
            null,
            derivationDepth,
            derivedValue,
            reason
        );
    }
}
