package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPhaseOneService;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

public final class AdminCatalogViewStateFactory {

    private final AdminCatalogPhaseOneService catalogService;
    private final AdminGeneratedReportLoader generatedReportLoader;

    public AdminCatalogViewStateFactory(final WildEconomyPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.catalogService = new AdminCatalogPhaseOneService(plugin);
        this.generatedReportLoader = new AdminGeneratedReportLoader();
    }

    public AdminCatalogViewState buildState(final boolean apply, final String actionName) throws IOException {
        final AdminCatalogBuildResult buildResult = this.catalogService.build(apply);
        final File generatedDirectory = buildResult.generatedDirectory();
        return new AdminCatalogViewState(
            buildResult,
            this.generatedReportLoader.loadRuleImpacts(new File(generatedDirectory, "generated-rule-impacts.yml")),
            this.generatedReportLoader.loadReviewBuckets(new File(generatedDirectory, "generated-review-buckets.yml")),
            actionName
        );
    }
}

