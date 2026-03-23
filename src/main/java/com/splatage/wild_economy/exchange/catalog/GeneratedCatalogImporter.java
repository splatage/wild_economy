package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.io.File;
import java.util.Map;

public final class GeneratedCatalogImporter {

    public Map<ItemKey, ExchangeCatalogEntry> importGeneratedCatalog(final File generatedCatalogFile) {
        if (generatedCatalogFile == null) {
            throw new IllegalArgumentException("generatedCatalogFile must not be null");
        }
        return Map.of();
    }
}
