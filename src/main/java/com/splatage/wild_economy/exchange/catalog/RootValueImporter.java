package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.io.File;
import java.math.BigDecimal;
import java.util.Map;

public final class RootValueImporter {

    public Map<ItemKey, BigDecimal> importRootValues(final File rootValuesFile) {
        if (rootValuesFile == null) {
            throw new IllegalArgumentException("rootValuesFile must not be null");
        }
        return Map.of();
    }
}
