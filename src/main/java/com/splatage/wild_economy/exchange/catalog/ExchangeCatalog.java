package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ExchangeCatalog {

    private static final Comparator<ExchangeCatalogEntry> DISPLAY_NAME_ORDER =
        ExchangeCatalog::compareEntriesByReverseItemKeySegments;

    private static final Comparator<GeneratedItemCategory> GENERATED_CATEGORY_ORDER =
        Comparator.comparing(GeneratedItemCategory::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(GeneratedItemCategory::name);

    private final Map<ItemKey, ExchangeCatalogEntry> entries;
    private final List<ExchangeCatalogEntry> allEntries;
    private final Map<ItemCategory, List<ExchangeCatalogEntry>> entriesByCategory;
    private final Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> entriesByCategoryAndGeneratedCategory;
    private final Map<ItemCategory, List<GeneratedItemCategory>> generatedSubcategoriesByCategory;

    public ExchangeCatalog(final Map<ItemKey, ExchangeCatalogEntry> entries) {
        Objects.requireNonNull(entries, "entries");

        this.entries = Map.copyOf(entries);

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
        return Optional.ofNullable(this.entries.get(itemKey));
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

        int leftIndex = leftSegments.length - 1;
        int rightIndex = rightSegments.length - 1;

        while (leftIndex >= 0 && rightIndex >= 0) {
            final int comparison = leftSegments[leftIndex].compareToIgnoreCase(rightSegments[rightIndex]);
            if (comparison != 0) {
                return comparison;
            }
            leftIndex--;
            rightIndex--;
        }

        if (leftIndex < 0 && rightIndex < 0) {
            return 0;
        }

        return Integer.compare(leftSegments.length, rightSegments.length);
    }

    private static String extractNamespace(final String itemKey) {
        final int colonIndex = itemKey.indexOf(':');
        if (colonIndex < 0) {
            return "";
        }
        return itemKey.substring(0, colonIndex);
    }

    private static String extractPath(final String itemKey) {
        final int colonIndex = itemKey.indexOf(':');
        if (colonIndex < 0 || colonIndex + 1 >= itemKey.length()) {
            return itemKey;
        }
        return itemKey.substring(colonIndex + 1);
    }

    private static Map<ItemCategory, List<ExchangeCatalogEntry>> freezeCategoryIndex(
        final Map<ItemCategory, List<ExchangeCatalogEntry>> categoryIndex
    ) {
        final Map<ItemCategory, List<ExchangeCatalogEntry>> frozen = new EnumMap<>(ItemCategory.class);
        for (final Map.Entry<ItemCategory, List<ExchangeCatalogEntry>> entry : categoryIndex.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> freezeSubcategoryIndex(
        final Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> subcategoryIndex
    ) {
        final Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> frozen =
            new EnumMap<>(ItemCategory.class);

        for (final Map.Entry<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> categoryEntry
            : subcategoryIndex.entrySet()) {
            final Map<GeneratedItemCategory, List<ExchangeCatalogEntry>> innerFrozen =
                new EnumMap<>(GeneratedItemCategory.class);

            for (final Map.Entry<GeneratedItemCategory, List<ExchangeCatalogEntry>> generatedEntry
                : categoryEntry.getValue().entrySet()) {
                innerFrozen.put(generatedEntry.getKey(), List.copyOf(generatedEntry.getValue()));
            }

            frozen.put(categoryEntry.getKey(), Collections.unmodifiableMap(innerFrozen));
        }

        return Collections.unmodifiableMap(frozen);
    }

    private static Map<ItemCategory, List<GeneratedItemCategory>> buildGeneratedSubcategoryIndex(
        final Map<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> subcategoryIndex
    ) {
        final Map<ItemCategory, List<GeneratedItemCategory>> result = new EnumMap<>(ItemCategory.class);

        for (final Map.Entry<ItemCategory, Map<GeneratedItemCategory, List<ExchangeCatalogEntry>>> categoryEntry
            : subcategoryIndex.entrySet()) {
            final List<GeneratedItemCategory> generatedCategories =
                new ArrayList<>(categoryEntry.getValue().keySet());
            generatedCategories.sort(GENERATED_CATEGORY_ORDER);
            result.put(categoryEntry.getKey(), List.copyOf(generatedCategories));
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }
}
