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
import java.util.List;
import java.util.Objects;

public final class ExchangeBrowseServiceImpl implements ExchangeBrowseService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;
    private final PricingService pricingService;
    private final LayoutBlueprint layoutBlueprint;
    private final LayoutPlacementResolver layoutPlacementResolver;

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
    }

    @Override
    public List<ExchangeCatalogView> browseLayout(
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final int pageSize
    ) {
        final List<ExchangeCatalogEntry> indexedEntries = this.indexedEntries(layoutGroupKey, layoutChildKey);
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
                this.resolveCurrentBuyPrice(entry, snapshot),
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
        final String layoutGroupKey,
        final String layoutChildKey
    ) {
        int count = 0;
        for (final ExchangeCatalogEntry entry : this.indexedEntries(layoutGroupKey, layoutChildKey)) {
            final StockSnapshot snapshot = this.stockService.getSnapshot(entry.itemKey());
            if (this.isPurchasableNow(entry, snapshot)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<String> listVisibleChildKeys(final String layoutGroupKey) {
        final List<LayoutChildDefinition> children = this.layoutBlueprint.orderedChildren(layoutGroupKey);
        if (children.isEmpty()) {
            return List.of();
        }

        final List<String> visibleChildren = new ArrayList<>();
        for (final LayoutChildDefinition child : children) {
            if (this.hasVisibleEntries(layoutGroupKey, child.key())) {
                visibleChildren.add(child.key());
            }
        }
        return List.copyOf(visibleChildren);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
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

    private List<ExchangeCatalogEntry> indexedEntries(
        final String layoutGroupKey,
        final String layoutChildKey
    ) {
        if (layoutGroupKey == null || layoutGroupKey.isBlank()) {
            return List.of();
        }

        final List<ExchangeCatalogEntry> matches = new ArrayList<>();
        for (final ExchangeCatalogEntry entry : this.exchangeCatalog.allEntries()) {
            final LayoutPlacement placement = this.layoutPlacementResolver.resolve(entry.itemKey());
            if (!layoutGroupKey.equalsIgnoreCase(placement.groupKey())) {
                continue;
            }
            if (layoutChildKey != null && !layoutChildKey.isBlank()) {
                if (!layoutChildKey.equalsIgnoreCase(placement.childKey())) {
                    continue;
                }
            }
            matches.add(entry);
        }
        return List.copyOf(matches);
    }

    private boolean hasVisibleEntries(
        final String layoutGroupKey,
        final String layoutChildKey
    ) {
        for (final ExchangeCatalogEntry entry : this.indexedEntries(layoutGroupKey, layoutChildKey)) {
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

    private BigDecimal resolveCurrentBuyPrice(
        final ExchangeCatalogEntry entry,
        final StockSnapshot snapshot
    ) {
        return this.pricingService.quoteBuy(entry.itemKey(), 1, snapshot).unitPrice();
    }
}
