package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.WorthImportConfig;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogLoader {

    private final WorthImporter worthImporter;
    private final CatalogMergeService mergeService;

    public CatalogLoader(final WorthImporter worthImporter, final CatalogMergeService mergeService) {
        this.worthImporter = worthImporter;
        this.mergeService = mergeService;
    }

    public ExchangeCatalog load(
        final ExchangeItemsConfig exchangeItemsConfig,
        final WorthImportConfig worthImportConfig
    ) {
        final Map<ItemKey, BigDecimal> importedWorths = this.worthImporter.importWorths(worthImportConfig);
        final Map<ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>();

        for (final ExchangeItemsConfig.RawItemEntry rawEntry : exchangeItemsConfig.items().values()) {
            final ExchangeCatalogEntry mergedEntry = this.mergeService.merge(rawEntry, importedWorths, worthImportConfig);
            entries.put(rawEntry.itemKey(), mergedEntry);
        }

        return new ExchangeCatalog(entries);
    }
}
