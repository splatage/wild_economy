package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogLoader {

    private final RootValueImporter rootValueImporter;
    private final CatalogMergeService mergeService;

    public CatalogLoader(final RootValueImporter rootValueImporter, final CatalogMergeService mergeService) {
        this.rootValueImporter = rootValueImporter;
        this.mergeService = mergeService;
    }

    public ExchangeCatalog load(final ExchangeItemsConfig exchangeItemsConfig, final File rootValuesFile) {
        final Map<ItemKey, BigDecimal> importedRootValues = this.rootValueImporter.importRootValues(rootValuesFile);
        final Map<ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>();

        for (final ExchangeItemsConfig.RawItemEntry rawEntry : exchangeItemsConfig.items().values()) {
            final ExchangeCatalogEntry mergedEntry = this.mergeService.merge(rawEntry, importedRootValues);
            entries.put(rawEntry.itemKey(), mergedEntry);
        }

        return new ExchangeCatalog(entries);
    }
}
