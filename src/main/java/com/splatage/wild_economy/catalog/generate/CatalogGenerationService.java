package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.classify.CategoryClassifier;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.GeneratedCatalogEntry;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.policy.PolicySuggestionService;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import com.splatage.wild_economy.catalog.scan.MaterialScanner;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CatalogGenerationService {

    private final MaterialScanner materialScanner;
    private final RootValueLookup rootValueLookup;
    private final CategoryClassifier categoryClassifier;
    private final PolicySuggestionService policySuggestionService;

    public CatalogGenerationService(
        final MaterialScanner materialScanner,
        final RootValueLookup rootValueLookup,
        final CategoryClassifier categoryClassifier,
        final PolicySuggestionService policySuggestionService
    ) {
        this.materialScanner = materialScanner;
        this.rootValueLookup = rootValueLookup;
        this.categoryClassifier = categoryClassifier;
        this.policySuggestionService = policySuggestionService;
    }

    public CatalogGenerationResult generate() {
        final List<ItemFacts> scanned = this.materialScanner.scanAll();
        final List<GeneratedCatalogEntry> generated = new ArrayList<>(scanned.size());

        int disabledCount = 0;
        int missingRootValueCount = 0;

        for (final ItemFacts facts : scanned) {
            final CatalogCategory category = this.categoryClassifier.classify(facts);
            final BigDecimal rootValue = this.rootValueLookup.findRootValue(facts.key()).orElse(null);
            final CatalogPolicy policy = this.policySuggestionService.suggest(facts, category, rootValue);
            final String includeReason = this.policySuggestionService.includeReason(facts, category, rootValue, policy);
            final String excludeReason = this.policySuggestionService.excludeReason(facts, category, rootValue, policy);

            if (policy == CatalogPolicy.DISABLED) {
                disabledCount++;
            }
            if (!facts.hasRootValue()) {
                missingRootValueCount++;
            }

            generated.add(new GeneratedCatalogEntry(
                facts.key(),
                category,
                policy,
                facts.hasRootValue(),
                rootValue,
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
            missingRootValueCount
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
