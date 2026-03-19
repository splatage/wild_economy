package com.splatage.wild_economy.exchange.pricing;

import com.splatage.wild_economy.exchange.domain.BuyQuote;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.SellQuote;
import com.splatage.wild_economy.exchange.domain.StockSnapshot;

public final class PricingServiceImpl implements PricingService {

    @Override
    public BuyQuote quoteBuy(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public SellQuote quoteSell(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
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

    private final ExchangeCatalog exchangeCatalog;

    public PricingServiceImpl(final ExchangeCatalog exchangeCatalog) {
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
    }

    @Override
    public BuyQuote quoteBuy(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final BigDecimal unitPrice = this.nonNullPrice(entry.buyPrice());
        final BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
        return new BuyQuote(itemKey, amount, unitPrice, totalPrice);
    }

    @Override
    public SellQuote quoteSell(final ItemKey itemKey, final int amount, final StockSnapshot stockSnapshot) {
        final ExchangeCatalogEntry entry = this.exchangeCatalog.get(itemKey)
            .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + itemKey.value()));

        final BigDecimal baseUnitPrice = this.nonNullPrice(entry.sellPrice());
        final BigDecimal multiplier = this.resolveSellMultiplier(entry.sellPriceBands(), stockSnapshot.fillRatio());
        final BigDecimal effectiveUnitPrice = baseUnitPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        final BigDecimal totalPrice = effectiveUnitPrice.multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
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

    private BigDecimal resolveSellMultiplier(final List<SellPriceBand> bands, final double fillRatio) {
        if (bands == null || bands.isEmpty()) {
            return BigDecimal.ONE;
        }
        for (final SellPriceBand band : bands) {
            if (fillRatio >= band.minFillRatioInclusive() && fillRatio < band.maxFillRatioExclusive()) {
                return band.multiplier();
            }
        }
        return BigDecimal.ONE;
    }

    private BigDecimal nonNullPrice(final BigDecimal price) {
        return price == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : price.setScale(2, RoundingMode.HALF_UP);
    }
}
