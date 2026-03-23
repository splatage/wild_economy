package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.EcoEnvelopesConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.StockProfilesConfig;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogLoader {

    private final GeneratedCatalogImporter generatedCatalogImporter;
    private final RootValueImporter rootValueImporter;
    private final CatalogMergeService mergeService;

    public CatalogLoader(
        final GeneratedCatalogImporter generatedCatalogImporter,
        final RootValueImporter rootValueImporter,
        final CatalogMergeService mergeService
    ) {
        this.generatedCatalogImporter = generatedCatalogImporter;
        this.rootValueImporter = rootValueImporter;
        this.mergeService = mergeService;
    }

    public ExchangeCatalog load(
        final ExchangeItemsConfig exchangeItemsConfig,
        final File rootValuesFile,
        final File generatedCatalogFile,
        final EcoEnvelopesConfig ecoEnvelopesConfig,
        final StockProfilesConfig stockProfilesConfig
    ) {
        final Map<ItemKey, BigDecimal> importedRootValues = this.rootValueImporter.importRootValues(rootValuesFile);
        final Map<ItemKey, ExchangeCatalogEntry> entries = new LinkedHashMap<>(
            this.generatedCatalogImporter.importGeneratedCatalog(generatedCatalogFile)
        );

        for (final ExchangeItemsConfig.RawItemEntry rawEntry : exchangeItemsConfig.items().values()) {
            final ExchangeCatalogEntry baseEntry = entries.get(rawEntry.itemKey());
            final ExchangeCatalogEntry mergedEntry = this.mergeService.merge(
                baseEntry,
                rawEntry,
                importedRootValues,
                ecoEnvelopesConfig,
                stockProfilesConfig
            );
            entries.put(rawEntry.itemKey(), mergedEntry);
        }

        return new ExchangeCatalog(entries);
    }
}
