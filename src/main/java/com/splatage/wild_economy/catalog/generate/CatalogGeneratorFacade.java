package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.classify.DefaultCategoryClassifier;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.policy.DefaultPolicySuggestionService;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLoader;
import com.splatage.wild_economy.catalog.scan.BukkitMaterialScanner;
import java.io.File;
import java.io.IOException;
import org.bukkit.plugin.java.JavaPlugin;

public final class CatalogGeneratorFacade {

    private final JavaPlugin plugin;

    public CatalogGeneratorFacade(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CatalogGenerationResult generateFromRootValuesFile(final File rootValuesFile) throws IOException {
        final RootValueLoader rootValueLoader = RootValueLoader.fromFile(rootValuesFile);

        final CatalogGenerationService service = new CatalogGenerationService(
            new BukkitMaterialScanner(rootValueLoader),
            rootValueLoader,
            new DefaultCategoryClassifier(),
            new DefaultPolicySuggestionService()
        );

        return service.generate();
    }

    public void writeOutputs(final CatalogGenerationResult result) throws IOException {
        final CatalogFileWriter writer = new CatalogFileWriter(this.plugin);
        writer.writeGeneratedCatalog(result);
        writer.writeSummary(result);
    }
}
