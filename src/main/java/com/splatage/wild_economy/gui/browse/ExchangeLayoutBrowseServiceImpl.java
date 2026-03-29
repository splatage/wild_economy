package com.splatage.wild_economy.gui.browse;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.StockState;
import com.splatage.wild_economy.exchange.service.ExchangeBrowseService;
import com.splatage.wild_economy.exchange.service.ExchangeCatalogView;
import com.splatage.wild_economy.exchange.service.ExchangeItemView;
import com.splatage.wild_economy.exchange.stock.StockService;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import com.splatage.wild_economy.gui.layout.LayoutChildDefinition;
import com.splatage.wild_economy.gui.layout.LayoutGroupDefinition;
import com.splatage.wild_economy.gui.layout.LayoutPlacement;
import com.splatage.wild_economy.gui.layout.LayoutPlacementResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExchangeLayoutBrowseServiceImpl implements ExchangeLayoutBrowseService {

    private static final long PREWARM_DELAY_MILLIS = 50L;

    private final ExchangeCatalog exchangeCatalog;
    private final ExchangeBrowseService exchangeBrowseService;
    private final StockService stockService;
    private final LayoutBlueprint layoutBlueprint;
    private final LayoutPlacementResolver layoutPlacementResolver;
    private final Map<LayoutScopeKey, List<ExchangeCatalogEntry>> indexedEntriesCache;
    private final Map<LayoutScopeKey, List<ExchangeCatalogView>> visibleEntriesCache;
    private final Map<String, List<String>> visibleChildKeysCache;
    private final Set<UUID> activeViewerIds;
    private final ScheduledExecutorService prewarmScheduler;
    private volatile long cachedStockRevision;
    private volatile long scheduledPrewarmRevision;
    private volatile boolean shutdown;

    public ExchangeLayoutBrowseServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final ExchangeBrowseService exchangeBrowseService,
        final StockService stockService,
        final LayoutBlueprint layoutBlueprint,
        final LayoutPlacementResolver layoutPlacementResolver
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.exchangeBrowseService = Objects.requireNonNull(exchangeBrowseService, "exchangeBrowseService");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.layoutBlueprint = Objects.requireNonNull(layoutBlueprint, "layoutBlueprint");
        this.layoutPlacementResolver = Objects.requireNonNull(layoutPlacementResolver, "layoutPlacementResolver");
        this.indexedEntriesCache = new ConcurrentHashMap<>();
        this.visibleEntriesCache = new ConcurrentHashMap<>();
        this.visibleChildKeysCache = new ConcurrentHashMap<>();
        this.activeViewerIds = ConcurrentHashMap.newKeySet();
        this.prewarmScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "wild-economy-layout-browse-prewarm");
            thread.setDaemon(true);
            return thread;
        });
        this.cachedStockRevision = Long.MIN_VALUE;
        this.scheduledPrewarmRevision = Long.MIN_VALUE;
        this.shutdown = false;
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
    public int countVisibleItems(final String layoutGroupKey, final String layoutChildKey) {
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
    public void handleExchangeViewOpened(final UUID playerId) {
        if (playerId == null || this.shutdown) {
            return;
        }
        this.activeViewerIds.add(playerId);
    }

    @Override
    public void handleExchangeViewClosed(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        this.activeViewerIds.remove(playerId);
        this.scheduleDeferredPrewarmIfDirty();
    }

    @Override
    public void shutdown() {
        this.shutdown = true;
        this.activeViewerIds.clear();
        this.prewarmScheduler.shutdownNow();
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
            final ExchangeItemView itemView = this.exchangeBrowseService.getItemView(entry.itemKey());
            if (!this.isPurchasableNow(entry, itemView)) {
                continue;
            }
            visibleEntries.add(new ExchangeCatalogView(
                itemView.itemKey(),
                itemView.displayName(),
                itemView.buyPrice(),
                itemView.stockCount(),
                itemView.stockState()
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

    private void scheduleDeferredPrewarmIfDirty() {
        if (this.shutdown || !this.activeViewerIds.isEmpty()) {
            return;
        }

        final long currentRevision = this.stockService.cacheRevision();
        if (currentRevision == this.cachedStockRevision) {
            return;
        }

        synchronized (this) {
            if (this.shutdown || !this.activeViewerIds.isEmpty()) {
                return;
            }
            final long revisionToPrewarm = this.stockService.cacheRevision();
            if (revisionToPrewarm == this.cachedStockRevision || this.scheduledPrewarmRevision == revisionToPrewarm) {
                return;
            }
            this.scheduledPrewarmRevision = revisionToPrewarm;
            this.prewarmScheduler.schedule(
                () -> this.runDeferredPrewarm(revisionToPrewarm),
                PREWARM_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
            );
        }
    }

    private void runDeferredPrewarm(final long expectedRevision) {
        if (this.shutdown) {
            return;
        }

        if (!this.activeViewerIds.isEmpty()) {
            this.clearScheduledRevision(expectedRevision);
            return;
        }

        final long currentRevision = this.stockService.cacheRevision();
        if (currentRevision != expectedRevision) {
            this.clearScheduledRevision(expectedRevision);
            if (currentRevision != this.cachedStockRevision && this.activeViewerIds.isEmpty()) {
                this.scheduleDeferredPrewarmIfDirty();
            }
            return;
        }

        try {
            this.prewarmVisibleBrowseCaches();
        } finally {
            this.clearScheduledRevision(expectedRevision);
        }
    }

    private void clearScheduledRevision(final long expectedRevision) {
        synchronized (this) {
            if (this.scheduledPrewarmRevision == expectedRevision) {
                this.scheduledPrewarmRevision = Long.MIN_VALUE;
            }
        }
    }

    private void prewarmVisibleBrowseCaches() {
        this.resetCachesIfRevisionChanged();
        for (final LayoutGroupDefinition group : this.layoutBlueprint.orderedGroups()) {
            this.countVisibleItems(group.key(), null);
            this.listVisibleChildKeys(group.key());
        }
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
            this.cachedStockRevision = currentRevision;
        }
    }

    private boolean isPurchasableNow(final ExchangeCatalogEntry entry, final ExchangeItemView itemView) {
        if (!itemView.buyEnabled()) {
            return false;
        }
        if (entry.policyMode() == ItemPolicyMode.UNLIMITED_BUY) {
            return true;
        }
        return itemView.stockState() != StockState.OUT_OF_STOCK;
    }

    private record LayoutScopeKey(String layoutGroupKey, String layoutChildKey) {}
}
