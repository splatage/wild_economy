package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.List;
import java.util.UUID;

public final class ExchangeServiceImpl implements ExchangeService {

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(final ItemCategory category, final int page, final int pageSize) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.stock.StockService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ExchangeServiceImpl implements ExchangeService {

    private final ExchangeCatalog exchangeCatalog;
    private final StockService stockService;
    private final ExchangeSellService exchangeSellService;

    public ExchangeServiceImpl(
        final ExchangeCatalog exchangeCatalog,
        final StockService stockService,
        final ExchangeSellService exchangeSellService
    ) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.stockService = Objects.requireNonNull(stockService, "stockService");
        this.exchangeSellService = Objects.requireNonNull(exchangeSellService, "exchangeSellService");
    }

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        return this.exchangeSellService.sellHand(playerId);
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        return this.exchangeSellService.sellAll(playerId);
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        throw new UnsupportedOperationException("Buy path not implemented yet");
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(final ItemCategory category, final int page, final int pageSize) {
        return this.exchangeCatalog.byCategory(category).stream()
            .skip((long) Math.max(0, page) * Math.max(1, pageSize))
            .limit(Math.max(1, pageSize))
            .map(this::toCatalogView)
            .collect(Collectors.toList());
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));
        final var snapshot = this.stockService.getSnapshot(itemKey);

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

    private ExchangeCatalogView toCatalogView(final ExchangeCatalogEntry entry) {
        final var snapshot = this.stockService.getSnapshot(entry.itemKey());
        return new ExchangeCatalogView(
            entry.itemKey(),
            entry.displayName(),
            entry.buyPrice(),
            snapshot.stockCount(),
            snapshot.stockState()
        );
    }
}
package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ExchangeServiceImpl implements ExchangeService {

    private final ExchangeBrowseService exchangeBrowseService;
    private final ExchangeBuyService exchangeBuyService;
    private final ExchangeSellService exchangeSellService;

    public ExchangeServiceImpl(
        final ExchangeBrowseService exchangeBrowseService,
        final ExchangeBuyService exchangeBuyService,
        final ExchangeSellService exchangeSellService
    ) {
        this.exchangeBrowseService = Objects.requireNonNull(exchangeBrowseService, "exchangeBrowseService");
        this.exchangeBuyService = Objects.requireNonNull(exchangeBuyService, "exchangeBuyService");
        this.exchangeSellService = Objects.requireNonNull(exchangeSellService, "exchangeSellService");
    }

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        return this.exchangeSellService.sellHand(playerId);
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        return this.exchangeSellService.sellAll(playerId);
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        return this.exchangeBuyService.buy(playerId, itemKey, amount);
    }

    @Override
    public List<ExchangeCatalogView> browseCategory(final ItemCategory category, final int page, final int pageSize) {
        return this.exchangeBrowseService.browseCategory(category, page, pageSize);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        return this.exchangeBrowseService.getItemView(itemKey);
    }
}
