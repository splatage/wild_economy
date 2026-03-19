package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.classify.CategoryClassifier;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.GeneratedCatalogEntry;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.policy.PolicySuggestionService;
import com.splatage.wild_economy.catalog.scan.MaterialScanner;
import com.splatage.wild_economy.catalog.worth.WorthPriceLookup;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CatalogGenerationService {

    private final MaterialScanner materialScanner;
    private final WorthPriceLookup worthPriceLookup;
    private final CategoryClassifier categoryClassifier;
    private final PolicySuggestionService policySuggestionService;

    public CatalogGenerationService(
        final MaterialScanner materialScanner,
        final WorthPriceLookup worthPriceLookup,
        final CategoryClassifier categoryClassifier,
        final PolicySuggestionService policySuggestionService
    ) {
        this.materialScanner = materialScanner;
        this.worthPriceLookup = worthPriceLookup;
        this.categoryClassifier = categoryClassifier;
        this.policySuggestionService = policySuggestionService;
    }

    public CatalogGenerationResult generate() {
        final List<ItemFacts> scanned = this.materialScanner.scanAll();
        final List<GeneratedCatalogEntry> generated = new ArrayList<>(scanned.size());

        int disabledCount = 0;
        int missingWorthCount = 0;

        for (final ItemFacts facts : scanned) {
            final CatalogCategory category = this.categoryClassifier.classify(facts);
            final BigDecimal basePrice = this.worthPriceLookup.findPrice(facts.key()).orElse(null);
            final CatalogPolicy policy = this.policySuggestionService.suggest(facts, category, basePrice);
            final String includeReason = this.policySuggestionService.includeReason(facts, category, basePrice, policy);
            final String excludeReason = this.policySuggestionService.excludeReason(facts, category, basePrice, policy);

            if (policy == CatalogPolicy.DISABLED) {
                disabledCount++;
            }
            if (!facts.hasWorthEntry()) {
                missingWorthCount++;
            }

            generated.add(new GeneratedCatalogEntry(
                facts.key(),
                category,
                policy,
                facts.hasWorthEntry(),
                basePrice,
                includeReason,
                excludeReason,
                buildNotes(facts)
            ));
        }

        generated.sort(
            Comparator.comparing(GeneratedCatalogEntry::category)
                .thenComparing(GeneratedCatalogEntry::key)
        );

        return new CatalogGenerationResult(
            List.copyOf(generated),
            scanned.size(),
            generated.size(),
            disabledCount,
            missingWorthCount
        );
    }

    private String buildNotes(final ItemFacts facts) {
        final List<String> notes = new ArrayList<>(4);
        if (facts.isBlock()) {
            notes.add("block-item");
        }
        if (facts.edible()) {
            notes.add("edible");
        }
        if (facts.maxStackSize() == 1) {
            notes.add("unstackable");
        }
        if (facts.fuelCandidate()) {
            notes.add("fuel");
        }
        return String.join(", ", notes);
    }
}
