package com.splatage.wild_economy.catalog.worth;

import java.math.BigDecimal;
import java.util.Optional;

public interface WorthPriceLookup {
    Optional<BigDecimal> findPrice(String itemKey);
}
