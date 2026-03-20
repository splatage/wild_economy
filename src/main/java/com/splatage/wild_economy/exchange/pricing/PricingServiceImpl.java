package com.splatage.wild_economy.exchange.pricing;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public final class PricingServiceImpl implements PricingService {

    private static final int MONEY_SCALE = 2;
    private static final int INTERNAL_SCALE = 8;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal TWO = BigDecimal.valueOf(2L);
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);

    private final ExchangeCatalog exchangeCatalog;

    public PricingServiceImpl(final ExchangeCatalog exchangeCatalog) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
    }

    @Override
    public BuyQuote quoteBuy(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final BigDecimal unitPrice = this.nonNullPrice(entry.buyPrice());
        final BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(amount)).setScale(MONEY_SCALE, MONEY_ROUNDING);
        return new BuyQuote(itemKey, amount, unitPrice, totalPrice);
    }

    @Override
    public SellQuote quoteSell(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final BigDecimal baseUnitPrice = this.nonNullPrice(entry.sellPrice());
        if (amount <= 0 || baseUnitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new SellQuote(itemKey, amount, baseUnitPrice, ZERO_MONEY, ZERO_MONEY, stockSnapshot.fillRatio(), false);
        }

        final BigDecimal totalPrice = this.resolveSellTotalPrice(entry, amount, stockSnapshot, baseUnitPrice);
        final BigDecimal effectiveUnitPrice = totalPrice.divide(
            BigDecimal.valueOf(amount),
            MONEY_SCALE,
            MONEY_ROUNDING
        );
        final boolean tapered = effectiveUnitPrice.compareTo(baseUnitPrice) < 0;

        return new SellQuote(
            itemKey,
            amount,
            baseUnitPrice,
            effectiveUnitPrice,
            totalPrice,
            stockSnapshot.fillRatio(),
            tapered
        );
    }

    private BigDecimal resolveSellTotalPrice(
        final ExchangeCatalogEntry entry,
        final int amount,
        final StockSnapshot stockSnapshot,
        final BigDecimal baseUnitPrice
    ) {
        final long startStock = Math.max(0L, stockSnapshot.stockCount());
        final long stockCap = Math.max(0L, stockSnapshot.stockCap());

        if (stockCap <= 0L) {
            final BigDecimal startUnitPrice = this.resolveSellUnitPrice(entry, baseUnitPrice, 0.0D);
            return startUnitPrice.multiply(BigDecimal.valueOf(amount)).setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        final long endStock = startStock + amount;
        final BigDecimal startUnitPrice = this.resolveSellUnitPrice(entry, baseUnitPrice, this.fillRatio(startStock, stockCap));
        final BigDecimal floorUnitPrice = this.resolveSellUnitPrice(entry, baseUnitPrice, 1.0D);

        if (startStock >= stockCap) {
            return floorUnitPrice.multiply(BigDecimal.valueOf(amount)).setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        if (endStock <= stockCap) {
            final BigDecimal endUnitPrice = this.resolveSellUnitPrice(entry, baseUnitPrice, this.fillRatio(endStock, stockCap));
            return this.averageUnitPriceTotal(startUnitPrice, endUnitPrice, amount);
        }

        final long beforeCapAmount = Math.max(0L, stockCap - startStock);
        final long afterCapAmount = Math.max(0L, amount - beforeCapAmount);

        final BigDecimal beforeCapTotal = this.averageUnitPriceTotal(startUnitPrice, floorUnitPrice, beforeCapAmount);
        final BigDecimal afterCapTotal = floorUnitPrice
            .multiply(BigDecimal.valueOf(afterCapAmount))
            .setScale(MONEY_SCALE, MONEY_ROUNDING);

        return beforeCapTotal.add(afterCapTotal).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal averageUnitPriceTotal(
        final BigDecimal startUnitPrice,
        final BigDecimal endUnitPrice,
        final long amount
    ) {
        if (amount <= 0L) {
            return ZERO_MONEY;
        }

        return startUnitPrice
            .add(endUnitPrice)
            .multiply(BigDecimal.valueOf(amount))
            .divide(TWO, INTERNAL_SCALE, MONEY_ROUNDING)
            .setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal resolveSellUnitPrice(
        final ExchangeCatalogEntry entry,
        final BigDecimal baseUnitPrice,
        final double fillRatio
    ) {
        final BigDecimal multiplier = this.resolveSellMultiplier(entry.sellPriceBands(), fillRatio);
        return baseUnitPrice.multiply(multiplier);
    }

    private BigDecimal resolveSellMultiplier(final List<SellPriceBand> bands, final double fillRatio) {
        if (bands == null || bands.isEmpty()) {
            return BigDecimal.ONE;
        }

        for (final SellPriceBand band : bands) {
            if (fillRatio >= band.minFillRatioInclusive() && fillRatio < band.maxFillRatioExclusive()) {
                return this.nonNullMultiplier(band.multiplier());
            }
        }

        final SellPriceBand lastBand = bands.get(bands.size() - 1);
        if (fillRatio >= lastBand.minFillRatioInclusive()) {
            return this.nonNullMultiplier(lastBand.multiplier());
        }

        return BigDecimal.ONE;
    }

    private double fillRatio(final long stockCount, final long stockCap) {
        if (stockCap <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) Math.max(0L, stockCount) / (double) stockCap);
    }

    private BigDecimal nonNullPrice(final BigDecimal price) {
        return price == null ? ZERO_MONEY : price.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal nonNullMultiplier(final BigDecimal multiplier) {
        return multiplier == null ? BigDecimal.ONE : multiplier;
    }
}
