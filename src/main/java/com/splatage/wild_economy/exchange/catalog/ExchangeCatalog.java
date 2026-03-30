package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.catalog.rootvalue.RootValueLoader;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.item.ExchangeItemCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ExchangeCatalog {

    private static final Comparator<ExchangeCatalogEntry> DISPLAY_NAME_ORDER =
        ExchangeCatalog::compareEntriesByReverseItemKeySegments;

    private static final Comparator<GeneratedItemCategory> GENERATED_CATEGORY_ORDER =
        Comparator.comparing(GeneratedItemCategory::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(GeneratedItemCategory::name);

    private final ExchangeItemCodec exchangeItemCodec;
    private final ExchangeVariantWorthResolver variantWorthResolver;
    private final Map<ItemKey, ExchangeCatalogEntry> entries;
    private final Map<ItemKey, ExchangeCatalogEntry> derivedEntries;
    private final List<ExchangeCatalogEntry> allEntries;
    private final Map<ItemCategory, List<ExchangeCatalogEntry>> entriesByCategory;
    private final Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> entriesByCategoryAndGeneratedCategory;
    private final Map<ItemCategory, List<GeneratedItemCategory>> generatedSubcategoriesByCategory;

    public ExchangeCatalog(final Map<ItemKey, ExchangeCatalogEntry> entries) {
        this(entries, RootValueLoader.empty());
    }

    public ExchangeCatalog(final Map<ItemKey, ExchangeCatalogEntry> entries, final RootValueLookup rootValueLookup) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(rootValueLookup, "rootValueLookup");

        this.exchangeItemCodec = new ExchangeItemCodec();
        this.entries = Map.copyOf(entries);
        this.derivedEntries = new ConcurrentHashMap<>();
        this.variantWorthResolver = new ExchangeVariantWorthResolver(this.entries, rootValueLookup, this.exchangeItemCodec);

        final List<ExchangeCatalogEntry> sortedEntries = new ArrayList<>(this.entries.values());
        sortedEntries.sort(DISPLAY_NAME_ORDER);
        this.allEntries = List.copyOf(sortedEntries);

        final Map<ItemCategory, List<ExchangeCatalogEntry>> categoryIndex = new EnumMap<>(ItemCategory.class);
        final Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> subcategoryIndex =
            new EnumMap<>(ItemCategory.class);

        for (final ExchangeCatalogEntry entry : sortedEntries) {
            categoryIndex.computeIfAbsent(entry.category(), ignored -> new ArrayList<>()).add(entry);

            if (entry.generatedCategory() != null) {
                subcategoryIndex
                    .computeIfAbsent(entry.category(), ignored -> new EnumMap<>(GeneratedItemCategory.class))
                    .computeIfAbsent(entry.generatedCategory(), ignored -> new ArrayList<>())
                    .add(entry);
            }
        }

        this.entriesByCategory = freezeCategoryIndex(categoryIndex);
        this.entriesByCategoryAndGeneratedCategory = freezeSubcategoryIndex(subcategoryIndex);
        this.generatedSubcategoriesByCategory = buildGeneratedSubcategoryIndex(this.entriesByCategoryAndGeneratedCategory);
    }

    public Optional<ExchangeCatalogEntry> get(final ItemKey itemKey) {
        final ExchangeCatalogEntry exact = this.entries.get(itemKey);
        if (exact != null) {
            return Optional.of(exact);
        }

        final ExchangeCatalogEntry cachedDerived = this.derivedEntries.get(itemKey);
        if (cachedDerived != null) {
            return Optional.of(cachedDerived);
        }

        final ExchangeCatalogEntry derived = this.buildDerivedEntry(itemKey);
        if (derived == null) {
            return Optional.empty();
        }

        this.derivedEntries.putIfAbsent(itemKey, derived);
        return Optional.of(this.derivedEntries.get(itemKey));
    }

    public Collection<ExchangeCatalogEntry> allEntries() {
        return this.allEntries;
    }

    public List<ExchangeCatalogEntry> byCategory(final ItemCategory category) {
        return this.entriesByCategory.getOrDefault(category, List.of());
    }

    public List<ExchangeCatalogEntry> byCategoryAndGeneratedCategory(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory
    ) {
        if (generatedCategory == null) {
            return this.byCategory(category);
        }

        final Map<GeneratedItemCategory, List<ExchangeCatalogEntry>> byGeneratedCategory =
            this.entriesByCategoryAndGeneratedCategory.get(category);
        if (byGeneratedCategory == null) {
            return List.of();
        }

        return byGeneratedCategory.getOrDefault(generatedCategory, List.of());
    }

    public List<GeneratedItemCategory> generatedSubcategories(final ItemCategory category) {
        return this.generatedSubcategoriesByCategory.getOrDefault(category, List.of());
    }

    private ExchangeCatalogEntry buildDerivedEntry(final ItemKey itemKey) {
        final Optional<ItemKey> baseCatalogKey = this.exchangeItemCodec.baseCatalogKey(itemKey);
        if (baseCatalogKey.isEmpty()) {
            return null;
        }

        final ExchangeCatalogEntry baseEntry = this.entries.get(baseCatalogKey.get());
        if (baseEntry == null) {
            return null;
        }

        final String displayName = this.exchangeItemCodec.displayName(itemKey)
            .orElseGet(() -> baseEntry.displayName() == null || baseEntry.displayName().isBlank() ? itemKey.value() : baseEntry.displayName());
        final java.math.BigDecimal resolvedBaseWorth = this.variantWorthResolver.resolveVariantBaseWorth(itemKey).orElse(baseEntry.baseWorth());
        final ExchangeCatalogEntry.ResolvedEcoEntry resolvedEco = this.scaleResolvedEco(baseEntry, resolvedBaseWorth);
        return new ExchangeCatalogEntry(
            itemKey,
            displayName,
            baseEntry.category(),
            baseEntry.generatedCategory(),
            baseEntry.policyMode(),
            resolvedBaseWorth,
            baseEntry.stockCap(),
            baseEntry.turnoverAmountPerInterval(),
            baseEntry.buyEnabled(),
            baseEntry.sellEnabled(),
            resolvedEco
        );
    }

    private ExchangeCatalogEntry.ResolvedEcoEntry scaleResolvedEco(
        final ExchangeCatalogEntry baseEntry,
        final java.math.BigDecimal derivedBaseWorth
    ) {
        if (derivedBaseWorth == null || baseEntry.baseWorth() == null || baseEntry.baseWorth().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return baseEntry.eco();
        }

        final java.math.BigDecimal baseWorth = baseEntry.baseWorth();
        return new ExchangeCatalogEntry.ResolvedEcoEntry(
            baseEntry.eco().minStockInclusive(),
            baseEntry.eco().maxStockInclusive(),
            scaleResolvedPrice(baseEntry.eco().buyPriceLowStock(), baseWorth, derivedBaseWorth),
            scaleResolvedPrice(baseEntry.eco().buyPriceHighStock(), baseWorth, derivedBaseWorth),
            scaleResolvedPrice(baseEntry.eco().sellPriceLowStock(), baseWorth, derivedBaseWorth),
            scaleResolvedPrice(baseEntry.eco().sellPriceHighStock(), baseWorth, derivedBaseWorth)
        );
    }

    private static java.math.BigDecimal scaleResolvedPrice(
        final java.math.BigDecimal baseResolvedPrice,
        final java.math.BigDecimal baseWorth,
        final java.math.BigDecimal derivedBaseWorth
    ) {
        if (baseResolvedPrice == null) {
            return null;
        }
        if (baseWorth.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return baseResolvedPrice;
        }
        return baseResolvedPrice
            .multiply(derivedBaseWorth)
            .divide(baseWorth, 2, java.math.RoundingMode.HALF_UP)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static int compareEntriesByReverseItemKeySegments(
        final ExchangeCatalogEntry left,
        final ExchangeCatalogEntry right
    ) {
        final String leftKey = left.itemKey().value();
        final String rightKey = right.itemKey().value();

        final int pathComparison = comparePathsByReverseSegments(extractPath(leftKey), extractPath(rightKey));
        if (pathComparison != 0) {
            return pathComparison;
        }

        final int namespaceComparison = extractNamespace(leftKey).compareToIgnoreCase(extractNamespace(rightKey));
        if (namespaceComparison != 0) {
            return namespaceComparison;
        }

        return leftKey.compareToIgnoreCase(rightKey);
    }

    private static int comparePathsByReverseSegments(final String leftPath, final String rightPath) {
        final String[] leftSegments = leftPath.split("_");
        final String[] rightSegments = rightPath.split("_");
        final int maxSegments = Math.max(leftSegments.length, rightSegments.length);

        for (int i = 1; i <= maxSegments; i++) {
            final String leftSegment = segmentFromEnd(leftSegments, i);
            final String rightSegment = segmentFromEnd(rightSegments, i);
            if (leftSegment == null && rightSegment == null) {
                continue;
            }
            if (leftSegment == null) {
                return -1;
            }
            if (rightSegment == null) {
                return 1;
            }
            final int comparison = leftSegment.compareToIgnoreCase(rightSegment);
            if (comparison != 0) {
                return comparison;
            }
        }

        return leftPath.compareToIgnoreCase(rightPath);
    }

    private static String segmentFromEnd(final String[] segments, final int offsetFromEnd) {
        final int index = segments.length - offsetFromEnd;
        if (index < 0 || index >= segments.length) {
            return null;
        }
        return segments[index];
    }

    private static String extractNamespace(final String namespacedKey) {
        final int separator = namespacedKey.indexOf(':');
        return separator >= 0 ? namespacedKey.substring(0, separator) : "minecraft";
    }

    private static String extractPath(final String namespacedKey) {
        final int separator = namespacedKey.indexOf(':');
        return separator >= 0 ? namespacedKey.substring(separator + 1) : namespacedKey;
    }

    private static Map<ItemCategory, List<ExchangeCatalogEntry>> freezeCategoryIndex(
        final Map<ItemCategory, List<ExchangeCatalogEntry>> source
    ) {
        final Map<ItemCategory, List<ExchangeCatalogEntry>> frozen = new EnumMap<>(ItemCategory.class);
        for (final Map.Entry<ItemCategory, List<ExchangeCatalogEntry>> entry : source.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> freezeSubcategoryIndex(
        final Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> source
    ) {
        final Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> frozen =
            new EnumMap<>(ItemCategory.class);
        for (final Map.Entry<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> categoryEntry : source.entrySet()) {
            final Map<GeneratedItemCategory, List<ExchangeCatalogEntry>> frozenChildren =
                new EnumMap<>(GeneratedItemCategory.class);
            for (final Map.Entry<GeneratedItemCategory, List<ExchangeCatalogEntry>> childEntry : categoryEntry.getValue().entrySet()) {
                frozenChildren.put(childEntry.getKey(), List.copyOf(childEntry.getValue()));
            }
            frozen.put(categoryEntry.getKey(), Collections.unmodifiableMap(frozenChildren));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static Map<ItemCategory, List<GeneratedItemCategory>> buildGeneratedSubcategoryIndex(
        final Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> subcategoryIndex
    ) {
        final Map<ItemCategory, List<GeneratedItemCategory>> result = new EnumMap<>(ItemCategory.class);
        for (final Map.Entry<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> entry : subcategoryIndex.entrySet()) {
            final List<GeneratedItemCategory> ordered = new ArrayList<>(entry.getValue().keySet());
            ordered.sort(GENERATED_CATEGORY_ORDER);
            result.put(entry.getKey(), List.copyOf(ordered));
        }
        return Collections.unmodifiableMap(result);
    }
}
