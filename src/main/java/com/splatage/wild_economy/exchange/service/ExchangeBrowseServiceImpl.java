package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import com.splatage.wild_economy.exchange.domain.StockState;
import com.splatage.wild_economy.exchange.pricing.PricingService;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import com.splatage.wild_economy.gui.layout.LayoutChildDefinition;
import com.splatage.wild_economy.gui.layout.LayoutPlacement;
import com.splatage.wild_economy.gui.layout.LayoutPlacementResolver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ExchangeBrowseServiceImpl implements ExchangeBrowseService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;
    private final PricingService pricingService;
    private final LayoutBlueprint layoutBlueprint;
    private final LayoutPlacementResolver layoutPlacementResolver;
    private final Map<LayoutScopeKey, List<ExchangeCatalogEntry>> indexedEntriesCache;
    private final Map<LayoutScopeKey, List<ExchangeCatalogView>> visibleEntriesCache;
    private final Map<String, List<String>> visibleChildKeysCache;
    private final Map<ItemKey, ExchangeItemView> itemViewCache;
    private volatile long cachedStockRevision;

    public ExchangeBrowseServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService,
        final PricingService pricingService,
        final LayoutBlueprint layoutBlueprint,
        final LayoutPlacementResolver layoutPlacementResolver
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.layoutBlueprint = Objects.requireNonNull(layoutBlueprint, "layoutBlueprint");
        this.layoutPlacementResolver = Objects.requireNonNull(layoutPlacementResolver, "layoutPlacementResolver");
        this.indexedEntriesCache = new ConcurrentHashMap<>();
        this.visibleEntriesCache = new ConcurrentHashMap<>();
        this.visibleChildKeysCache = new ConcurrentHashMap<>();
        this.itemViewCache = new ConcurrentHashMap<>();
        this.cachedStockRevision = Long.MIN_VALUE;
    }

    @Override
    public List<ExchangeCatalogView> browseLayout(
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final int pageSize
    ) {
        this.resetCachesIfRevisionChanged();
        final List<ExchangeCatalogView> visibleEntries = this.visibleEntries(layoutGroupKey, layoutChildKey);
        if (visibleEntries.isEmpty()) {
            return List.of();
        }

        final int safePage = Math.max(0, page);
        final int safePageSize = Math.max(1, pageSize);
        final int fromIndex = Math.min(visibleEntries.size(), safePage * safePageSize);
        final int toIndex = Math.min(visibleEntries.size(), fromIndex + safePageSize);
        if (fromIndex >= toIndex) {
            return List.of();
        }
        return List.copyOf(visibleEntries.subList(fromIndex, toIndex));
    }

    @Override
    public int countVisibleItems(
        final String layoutGroupKey,
        final String layoutChildKey
    ) {
        this.resetCachesIfRevisionChanged();
        return this.visibleEntries(layoutGroupKey, layoutChildKey).size();
    }

    @Override
    public List<String> listVisibleChildKeys(final String layoutGroupKey) {
        this.resetCachesIfRevisionChanged();
        if (layoutGroupKey == null || layoutGroupKey.isBlank()) {
            return List.of();
        }
        return this.visibleChildKeysCache.computeIfAbsent(layoutGroupKey, this::computeVisibleChildKeys);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        this.resetCachesIfRevisionChanged();
        return this.itemViewCache.computeIfAbsent(itemKey, this::buildItemView);
    }

    private List<ExchangeCatalogEntry> indexedEntries(
        final String layoutGroupKey,
        final String layoutChildKey
    ) {
        if (layoutGroupKey == null || layoutGroupKey.isBlank()) {
            return List.of();
        }
        return this.indexedEntriesCache.computeIfAbsent(
            new LayoutScopeKey(layoutGroupKey, layoutChildKey),
            this::computeIndexedEntries
        );
    }

    private List<ExchangeCatalogEntry> computeIndexedEntries(final LayoutScopeKey scopeKey) {
        final List<ExchangeCatalogEntry> matches = new ArrayList<>();
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.allEntries()) {
            final LayoutPlacement placement = this.layoutPlacementResolver.resolve(entry.itemKey());
            if (!scopeKey.layoutGroupKey().equalsIgnoreCase(placement.groupKey())) {
                continue;
            }
            if (scopeKey.layoutChildKey() != null && !scopeKey.layoutChildKey().isBlank()) {
                if (!scopeKey.layoutChildKey().equalsIgnoreCase(placement.childKey())) {
                    continue;
                }
            }
            matches.add(entry);
        }
        return List.copyOf(matches);
    }

    private List<ExchangeCatalogView> visibleEntries(
        final String layoutGroupKey,
        final String layoutChildKey
    ) {
        return this.visibleEntriesCache.computeIfAbsent(
            new LayoutScopeKey(layoutGroupKey, layoutChildKey),
            this::computeVisibleEntries
        );
    }

    private List<ExchangeCatalogView> computeVisibleEntries(final LayoutScopeKey scopeKey) {
        final List<ExchangeCatalogEntry> indexedEntries = this.indexedEntries(scopeKey.layoutGroupKey(), scopeKey.layoutChildKey());
        if (indexedEntries.isEmpty()) {
            return List.of();
        }
        final List<ExchangeCatalogView> visibleEntries = new ArrayList<>(indexedEntries.size());
        for (final ExchangeCatalogEntry entry : indexedEntries) {
            final StockSnapshot snapshot = this.stockService.getSnapshot(entry.itemKey());
            if (!this.isPurchasableNow(entry, snapshot)) {
                continue;
            }
            visibleEntries.add(new ExchangeCatalogView(
                entry.itemKey(),
                entry.displayName(),
                this.resolveCurrentBuyPrice(entry, snapshot),
                snapshot.stockCount(),
                snapshot.stockState()
            ));
        }
        return List.copyOf(visibleEntries);
    }

    private List<String> computeVisibleChildKeys(final String layoutGroupKey) {
        final List<LayoutChildDefinition> children = this.layoutBlueprint.orderedChildren(layoutGroupKey);
        if (children.isEmpty()) {
            return List.of();
        }

        final List<String> visibleChildren = new ArrayList<>();
        for (final LayoutChildDefinition child : children) {
            if (!this.visibleEntries(layoutGroupKey, child.key()).isEmpty()) {
                visibleChildren.add(child.key());
            }
        }
        return List.copyOf(visibleChildren);
    }

    private ExchangeItemView buildItemView(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));
        final StockSnapshot snapshot = this.stockService.getSnapshot(itemKey);
        return new ExchangeItemView(
            itemKey,
            entry.displayName(),
            this.resolveCurrentBuyPrice(entry, snapshot),
            snapshot.stockCount(),
            snapshot.stockCap(),
            snapshot.stockState(),
            entry.buyEnabled()
        );
    }

    private void resetCachesIfRevisionChanged() {
        final long currentRevision = this.stockService.cacheRevision();
        if (currentRevision == this.cachedStockRevision) {
            return;
        }
        synchronized (this) {
            if (currentRevision == this.cachedStockRevision) {
                return;
            }
            this.visibleEntriesCache.clear();
            this.visibleChildKeysCache.clear();
            this.itemViewCache.clear();
            this.cachedStockRevision = currentRevision;
        }
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

    private record LayoutScopeKey(String layoutGroupKey, String layoutChildKey) {}

    private BigDecimal resolveCurrentBuyPrice(
        final ExchangeCatalogEntry entry,
        final StockSnapshot snapshot
    ) {
        return this.pricingService.quoteBuy(entry.itemKey(), 1, snapshot).unitPrice();
    }
}
