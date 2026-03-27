package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import java.math.BigDecimal;
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
    public SellContainerResult sellContainer(final UUID playerId) {
        return this.exchangeSellService.sellContainer(playerId);
    }

    @Override
    public SellPreviewResult previewInventorySell(final UUID playerId) {
        return this.exchangeSellService.previewInventorySell(playerId);
    }

    @Override
    public BuyResult buy(final UUID playerId, final ItemKey itemKey, final int amount) {
        return this.exchangeBuyService.buy(playerId, itemKey, amount);
    }

    @Override
    public BuyResult buyQuoted(
        final UUID playerId,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal quotedUnitPrice
    ) {
        return this.exchangeBuyService.buyQuoted(playerId, itemKey, amount, quotedUnitPrice);
    }

    @Override
    public List<ExchangeCatalogView> browseLayout(
        final String layoutGroupKey,
        final String layoutChildKey,
        final int page,
        final int pageSize
    ) {
        return this.exchangeBrowseService.browseLayout(layoutGroupKey, layoutChildKey, page, pageSize);
    }

    @Override
    public int countVisibleItems(final String layoutGroupKey, final String layoutChildKey) {
        return this.exchangeBrowseService.countVisibleItems(layoutGroupKey, layoutChildKey);
    }

    @Override
    public List<String> listVisibleChildKeys(final String layoutGroupKey) {
        return this.exchangeBrowseService.listVisibleChildKeys(layoutGroupKey);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        return this.exchangeBrowseService.getItemView(itemKey);
    }
}
