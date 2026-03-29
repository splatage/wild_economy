package com.splatage.wild_economy.catalog.rootvalue;

import java.math.BigDecimal;
import java.util.Optional;

public interface RootValueLookup {
    Optional<BigDecimal> findRootValue(String itemKey);
}
