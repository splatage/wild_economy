package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.classify.DefaultCategoryClassifier;
import com.splatage.wild_economy.catalog.derive.RootAnchoredDerivationService;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.policy.DefaultPolicySuggestionService;
import com.splatage.wild_economy.catalog.recipe.BukkitRecipeGraphBuilder;
import com.splatage.wild_economy.catalog.recipe.RecipeGraph;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLoader;
import com.splatage.wild_economy.catalog.scan.BukkitMaterialScanner;
import java.io.File;
import java.io.IOException;
import org.bukkit.plugin.java.JavaPlugin;

public final class CatalogGeneratorFacade {

    private static final int DEFAULT_MAX_AUTO_INCLUSION_DEPTH = 1;

    private final JavaPlugin plugin;

    public CatalogGeneratorFacade(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CatalogGenerationResult generateFromRootValuesFile(final File rootValuesFile) throws IOException {
        final RootValueLoader rootValueLoader = RootValueLoader.fromFile(rootValuesFile);
        final RecipeGraph recipeGraph = new BukkitRecipeGraphBuilder().build();
        final RootAnchoredDerivationService derivationService = new RootAnchoredDerivationService(
            recipeGraph,
            rootValueLoader
        );

        final CatalogGenerationService service = new CatalogGenerationService(
            new BukkitMaterialScanner(rootValueLoader),
            new DefaultCategoryClassifier(),
            new DefaultPolicySuggestionService(DEFAULT_MAX_AUTO_INCLUSION_DEPTH),
            derivationService
        );

        return service.generate();
    }

    public void writeOutputs(final CatalogGenerationResult result) throws IOException {
        final CatalogFileWriter writer = new CatalogFileWriter(this.plugin);
        writer.writeGeneratedCatalog(result);
        writer.writeSummary(result);
    }
}
