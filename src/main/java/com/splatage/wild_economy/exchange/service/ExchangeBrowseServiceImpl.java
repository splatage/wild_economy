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
import java.math.BigDecimal;
import java.math.RoundingMode;
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
            this.resolveCurrentBuyPrice(entry, snapshot),
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
    private BigDecimal resolveCurrentBuyPrice(
        final ExchangeCatalogEntry entry,
        final StockSnapshot snapshot
    ) {
        if (entry.policyMode() != ItemPolicyMode.PLAYER_STOCKED) {
            return this.money(entry.eco().buyPriceLowStock());
        }

        return this.resolveEnvelopeUnitPrice(
            this.money(entry.eco().buyPriceLowStock()),
            this.money(entry.eco().buyPriceHighStock()),
            entry.eco().minStockInclusive(),
            entry.eco().maxStockInclusive(),
            snapshot.stockCount()
        );
    }

    private BigDecimal resolveEnvelopeUnitPrice(
        final BigDecimal lowStockPrice,
        final BigDecimal highStockPrice,
        final long minStock,
        final long maxStock,
        final long stock
    ) {
        if (stock <= minStock) {
            return lowStockPrice;
        }
        if (stock >= maxStock) {
            return highStockPrice;
        }
        if (maxStock <= minStock) {
            return lowStockPrice;
        }

        final BigDecimal range = BigDecimal.valueOf(maxStock - minStock);
        final BigDecimal offset = BigDecimal.valueOf(stock - minStock);
        final BigDecimal fraction = offset.divide(range, 8, RoundingMode.HALF_UP);
        final BigDecimal spread = highStockPrice.subtract(lowStockPrice);

        return lowStockPrice
            .add(spread.multiply(fraction))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(final BigDecimal value) {
        return value == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : value.setScale(2, RoundingMode.HALF_UP);
    }
}
