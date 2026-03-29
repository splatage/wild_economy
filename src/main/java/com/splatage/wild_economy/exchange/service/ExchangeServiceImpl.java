package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import java.math.BigDecimal;
import java.util.Objects;
import org.bukkit.entity.Player;

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
    public SellHandResult sellHand(final Player player) {
        return this.exchangeSellService.sellHand(player);
    }

    @Override
    public SellAllResult sellAll(final Player player) {
        return this.exchangeSellService.sellAll(player);
    }

    @Override
    public SellContainerResult sellContainer(final Player player) {
        return this.exchangeSellService.sellContainer(player);
    }

    @Override
    public SellPreviewResult previewInventorySell(final Player player) {
        return this.exchangeSellService.previewInventorySell(player);
    }

    @Override
    public SellPreviewResult previewContainerSell(final Player player) {
        return this.exchangeSellService.previewContainerSell(player);
    }

    @Override
    public BuyResult buy(final Player player, final ItemKey itemKey, final int amount) {
        return this.exchangeBuyService.buy(player, itemKey, amount);
    }

    @Override
    public BuyResult buyQuoted(
        final Player player,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal quotedUnitPrice
    ) {
        return this.exchangeBuyService.buyQuoted(player, itemKey, amount, quotedUnitPrice);
    }

    @Override
    public ExchangeItemView getItemView(final ItemKey itemKey) {
        return this.exchangeBrowseService.getItemView(itemKey);
    }
}
