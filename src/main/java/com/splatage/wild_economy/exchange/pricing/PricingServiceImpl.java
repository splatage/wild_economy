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
        final SellPriceBand envelope = this.resolveSellEnvelope(entry.sellPriceBands());
        if (envelope == null) {
            return baseUnitPrice.multiply(BigDecimal.valueOf(amount)).setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        final long startStock = Math.max(0L, stockSnapshot.stockCount());
        final long minStock = Math.max(0L, envelope.minStockInclusive());
        final long maxStock = Math.max(minStock, envelope.maxStockInclusive());
        final BigDecimal minUnitPrice = this.clampedFloorPrice(envelope.minUnitPrice(), baseUnitPrice);

        long remaining = amount;
        long currentStock = startStock;
        BigDecimal total = ZERO_MONEY;

        if (remaining > 0 && currentStock < minStock) {
            final long plateauAmount = Math.min(remaining, minStock - currentStock);
            total = total.add(
                baseUnitPrice.multiply(BigDecimal.valueOf(plateauAmount)).setScale(MONEY_SCALE, MONEY_ROUNDING)
            );
            currentStock += plateauAmount;
            remaining -= plateauAmount;
        }

        if (remaining > 0 && currentStock < maxStock) {
            final long linearAmount = Math.min(remaining, maxStock - currentStock);
            final BigDecimal startUnitPrice = this.resolveEnvelopeUnitPrice(baseUnitPrice, minUnitPrice, minStock, maxStock, currentStock);
            final BigDecimal endUnitPrice = this.resolveEnvelopeUnitPrice(baseUnitPrice, minUnitPrice, minStock, maxStock, currentStock + linearAmount);
            total = total.add(this.averageUnitPriceTotal(startUnitPrice, endUnitPrice, linearAmount));
            currentStock += linearAmount;
            remaining -= linearAmount;
        }

        if (remaining > 0) {
            total = total.add(
                minUnitPrice.multiply(BigDecimal.valueOf(remaining)).setScale(MONEY_SCALE, MONEY_ROUNDING)
            );
        }

        return total.setScale(MONEY_SCALE, MONEY_ROUNDING);
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

    private BigDecimal resolveEnvelopeUnitPrice(
        final BigDecimal maxUnitPrice,
        final BigDecimal minUnitPrice,
        final long minStock,
        final long maxStock,
        final long stock
    ) {
        if (stock <= minStock) {
            return maxUnitPrice;
        }

        if (stock >= maxStock) {
            return minUnitPrice;
        }

        if (maxStock <= minStock) {
            return minUnitPrice;
        }

        final BigDecimal range = BigDecimal.valueOf(maxStock - minStock);
        final BigDecimal offset = BigDecimal.valueOf(stock - minStock);
        final BigDecimal fraction = offset.divide(range, INTERNAL_SCALE, MONEY_ROUNDING);
        final BigDecimal spread = maxUnitPrice.subtract(minUnitPrice);

        return maxUnitPrice
            .subtract(spread.multiply(fraction))
            .setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private SellPriceBand resolveSellEnvelope(final List<SellPriceBand> bands) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }

        return bands.get(0);
    }

    private BigDecimal clampedFloorPrice(final BigDecimal configuredFloorPrice, final BigDecimal baseUnitPrice) {
        final BigDecimal normalizedFloor = this.nonNullPrice(configuredFloorPrice);
        if (normalizedFloor.compareTo(baseUnitPrice) > 0) {
            return baseUnitPrice;
        }
        return normalizedFloor;
    }

    private BigDecimal nonNullPrice(final BigDecimal price) {
        return price == null ? ZERO_MONEY : price.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
