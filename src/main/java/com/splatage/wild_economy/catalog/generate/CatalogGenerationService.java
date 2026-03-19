package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.classify.CategoryClassifier;
import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.derive.RootAnchoredDerivationService;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.GeneratedCatalogEntry;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.policy.PolicySuggestionService;
import com.splatage.wild_economy.catalog.scan.MaterialScanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CatalogGenerationService {

    private final MaterialScanner materialScanner;
    private final CategoryClassifier categoryClassifier;
    private final PolicySuggestionService policySuggestionService;
    private final RootAnchoredDerivationService derivationService;

    public CatalogGenerationService(
        final MaterialScanner materialScanner,
        final CategoryClassifier categoryClassifier,
        final PolicySuggestionService policySuggestionService,
        final RootAnchoredDerivationService derivationService
    ) {
        this.materialScanner = materialScanner;
        this.categoryClassifier = categoryClassifier;
        this.policySuggestionService = policySuggestionService;
        this.derivationService = derivationService;
    }

    public CatalogGenerationResult generate() {
        final List<ItemFacts> scanned = this.materialScanner.scanAll();
        final List<GeneratedCatalogEntry> generated = new ArrayList<>(scanned.size());

        int disabledCount = 0;
        int rootAnchoredCount = 0;
        int derivedIncludedCount = 0;

        for (final ItemFacts facts : scanned) {
            final CatalogCategory category = this.categoryClassifier.classify(facts);
            final DerivedItemResult derivation = this.derivationService.resolve(facts.key());
            final CatalogPolicy policy = this.policySuggestionService.suggest(facts, category, derivation);
            final String includeReason = this.policySuggestionService.includeReason(facts, category, derivation, policy);
            final String excludeReason = this.policySuggestionService.excludeReason(facts, category, derivation, policy);

            if (policy == CatalogPolicy.DISABLED) {
                disabledCount++;
            }
            if (derivation.reason() == com.splatage.wild_economy.catalog.derive.DerivationReason.ROOT_ANCHOR) {
                rootAnchoredCount++;
            }
            if (derivation.reason() == com.splatage.wild_economy.catalog.derive.DerivationReason.DERIVED_FROM_ROOT) {
                derivedIncludedCount++;
            }

            generated.add(new GeneratedCatalogEntry(
                facts.key(),
                category,
                policy,
                derivation.rootValuePresent(),
                derivation.rootValue(),
                derivation.derivationDepth(),
                derivation.derivedValue(),
                derivation.reason().name(),
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
            rootAnchoredCount,
            derivedIncludedCount
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
