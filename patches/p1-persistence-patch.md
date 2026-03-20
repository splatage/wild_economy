# wild_economy P1 browse/index patch set

Stage: **P1**

This patch set applies the locked P1 browse/index direction on top of the tested P0 persistence work.

Included in this slice:

* immutable precomputed category indexes in `ExchangeCatalog`
* immutable precomputed category + generated-subcategory indexes in `ExchangeCatalog`
* immutable precomputed sorted generated-subcategory lists in `ExchangeCatalog`
* browse service changed to consume precomputed indexes instead of rebuilding and resorting entry lists on each click
* browse service changed to use direct loops instead of repeated stream pipelines and transient `VisibleEntry` lists

Not included in this slice:

* page/result memoization
* multi-item stock snapshot batching API
* metrics for browse hot-path timing
* atomic stock-delta persistence work from P2

---

## File: `src/main/java/com/splatage/wild_economy/exchange/catalog/ExchangeCatalog.java`

```java
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
        Comparator.comparing(ExchangeCatalogEntry::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.itemKey().value());

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
```

## File: `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseServiceImpl.java`

```java
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.StockState;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExchangeBrowseServiceImpl implements ExchangeBrowseService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;

    public ExchangeBrowseServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final int pageSize
    ) {
        final List<ExchangeCatalogEntry> indexedEntries = this.indexedEntries(category, generatedCategory);
        if (indexedEntries.isEmpty()) {
            return List.of();
        }

        final int safePage = Math.max(0, page);
        final int safePageSize = Math.max(1, pageSize);
        final long toSkip = (long) safePage * safePageSize;

        long visibleIndex = 0L;
        final List<ExchangeCatalogView> pageEntries = new ArrayList<>(safePageSize);

        for (final ExchangeCatalogEntry entry : indexedEntries) {
            final StockSnapshot snapshot = this.stockService.getSnapshot(entry.itemKey());
            if (!this.isPurchasableNow(entry, snapshot)) {
                continue;
            }
            if (visibleIndex++ < toSkip) {
                continue;
            }

            pageEntries.add(new ExchangeCatalogView(
                entry.itemKey(),
                entry.displayName(),
                entry.buyPrice(),
                snapshot.stockCount(),
                snapshot.stockState()
            ));

            if (pageEntries.size() >= safePageSize) {
                break;
            }
        }

        return List.copyOf(pageEntries);
    }

    @Override
    public int countVisibleItems(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory
    ) {
        int count = 0;
        for (final ExchangeCatalogEntry entry : this.indexedEntries(category, generatedCategory)) {
            final StockSnapshot snapshot = this.stockService.getSnapshot(entry.itemKey());
            if (this.isPurchasableNow(entry, snapshot)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<GeneratedItemCategory> listVisibleSubcategories(final ItemCategory category) {
        final List<GeneratedItemCategory> indexedSubcategories = this.exchangeCatalog.generatedSubcategories(category);
        if (indexedSubcategories.isEmpty()) {
            return List.of();
        }

        final List<GeneratedItemCategory> visibleSubcategories = new ArrayList<>();
        for (final GeneratedItemCategory generatedCategory : indexedSubcategories) {
            if (this.hasVisibleEntries(category, generatedCategory)) {
                visibleSubcategories.add(generatedCategory);
            }
        }
        return List.copyOf(visibleSubcategories);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final StockSnapshot snapshot = this.stockService.getSnapshot(itemKey);
        return new ExchangeItemView(
            itemKey,
            entry.displayName(),
            entry.buyPrice(),
            snapshot.stockCount(),
            snapshot.stockCap(),
            snapshot.stockState(),
            entry.buyEnabled()
        );
    }

    private List<ExchangeCatalogEntry> indexedEntries(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory
    ) {
        return generatedCategory == null
            ? this.exchangeCatalog.byCategory(category)
            : this.exchangeCatalog.byCategoryAndGeneratedCategory(category, generatedCategory);
    }

    private boolean hasVisibleEntries(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory
    ) {
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.byCategoryAndGeneratedCategory(category, generatedCategory)) {
            final StockSnapshot snapshot = this.stockService.getSnapshot(entry.itemKey());
            if (this.isPurchasableNow(entry, snapshot)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPurchasableNow(
        final ExchangeCatalogEntry entry,
        final StockSnapshot snapshot
    ) {
        if (!entry.buyEnabled()) {
            return false;
        }
        if (entry.policyMode() == ItemPolicyMode.UNLIMITED_BUY) {
            return true;
        }
        return snapshot.stockState() != StockState.OUT_OF_STOCK;
    }
}
```
