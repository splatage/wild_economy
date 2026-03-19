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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
        final int safePage = Math.max(0, page);
        final int safePageSize = Math.max(1, pageSize);

        return this.visibleEntries(category, generatedCategory).stream()
            .skip((long) safePage * safePageSize)
            .limit(safePageSize)
            .map(visible -> new ExchangeCatalogView(
                visible.entry().itemKey(),
                visible.entry().displayName(),
                visible.entry().buyPrice(),
                visible.snapshot().stockCount(),
                visible.snapshot().stockState()
            ))
            .collect(Collectors.toList());
    }

    @Override
    public int countVisibleItems(final ItemCategory category, final GeneratedItemCategory generatedCategory) {
        return this.visibleEntries(category, generatedCategory).size();
    }

    @Override
    public List<GeneratedItemCategory> listVisibleSubcategories(final ItemCategory category) {
        return this.visibleEntries(category, null).stream()
            .map(visible -> visible.entry().generatedCategory())
            .filter(Objects::nonNull)
            .distinct()
            .sorted(Comparator.comparing(GeneratedItemCategory::displayName))
            .collect(Collectors.toList());
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

    private List<VisibleEntry> visibleEntries(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory
    ) {
        final List<ExchangeCatalogEntry> rawEntries = generatedCategory == null
            ? this.exchangeCatalog.byCategory(category)
            : this.exchangeCatalog.byCategoryAndGeneratedCategory(category, generatedCategory);

        return rawEntries.stream()
            .map(entry -> new VisibleEntry(entry, this.stockService.getSnapshot(entry.itemKey())))
            .filter(this::isPurchasableNow)
            .sorted(Comparator.comparing(visible -> visible.entry().displayName(), String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    private boolean isPurchasableNow(final VisibleEntry visible) {
        final ExchangeCatalogEntry entry = visible.entry();
        if (!entry.buyEnabled()) {
            return false;
        }
        if (entry.policyMode() == ItemPolicyMode.UNLIMITED_BUY) {
            return true;
        }
        return visible.snapshot().stockState() != StockState.OUT_OF_STOCK;
    }

    private record VisibleEntry(
        ExchangeCatalogEntry entry,
        StockSnapshot snapshot
    ) {
    }
}
