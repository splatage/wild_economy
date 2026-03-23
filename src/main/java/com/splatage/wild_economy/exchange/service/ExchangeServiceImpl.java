package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
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
    public List<ExchangeCatalogView> browseCategory(
        final ItemCategory category,
        final GeneratedItemCategory generatedCategory,
        final int page,
        final int pageSize
    ) {
        return this.exchangeBrowseService.browseCategory(category, generatedCategory, page, pageSize);
    }

    @Override
    public int countVisibleItems(final ItemCategory category, final GeneratedItemCategory generatedCategory) {
        return this.exchangeBrowseService.countVisibleItems(category, generatedCategory);
    }

    @Override
    public List<GeneratedItemCategory> listVisibleSubcategories(final ItemCategory category) {
        return this.exchangeBrowseService.listVisibleSubcategories(category);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        return this.exchangeBrowseService.getItemView(itemKey);
    }
}
