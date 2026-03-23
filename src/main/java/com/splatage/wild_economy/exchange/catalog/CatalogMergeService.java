package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.config.EcoEnvelopesConfig;
import com.splatage.wild_economy.config.ExchangeItemsConfig;
import com.splatage.wild_economy.config.StockProfilesConfig;
import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.SellPriceBand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class CatalogMergeService {
    private static final int MONEY_SCALE = 2;
    private static final int INTERNAL_SCALE = 8;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);

    public ExchangeCatalogEntry merge(
            final ExchangeCatalogEntry baseEntry,
            final ExchangeItemsConfig.RawItemEntry overrideEntry,
            final Map<ItemKey, BigDecimal> importedRootValues,
            final EcoEnvelopesConfig ecoEnvelopesConfig,
            final StockProfilesConfig stockProfilesConfig
    ) {
        final BigDecimal rootValue = importedRootValues.get(overrideEntry.itemKey());
        final BigDecimal baseWorth = this.resolveBaseWorth(baseEntry, rootValue);
        final EcoEnvelopesConfig.EcoEnvelopeDefinition ecoEnvelope =
                ecoEnvelopesConfig.get(overrideEntry.ecoEnvelopeKey()).orElse(null);

        final BigDecimal buyPrice = this.resolveBuyPrice(
                overrideEntry,
                baseEntry,
                baseWorth,
                rootValue,
                ecoEnvelope
        );
        final BigDecimal sellPrice = this.resolveSellPrice(
                overrideEntry,
                baseEntry,
                baseWorth,
                rootValue,
                ecoEnvelope
        );
        final SellPriceBand sellEnvelope = this.resolveSellEnvelope(
                overrideEntry,
                baseEntry,
                ecoEnvelope,
                sellPrice
        );

        final String displayName = overrideEntry.displayName() != null
                ? overrideEntry.displayName()
                : baseEntry != null
                        ? baseEntry.displayName()
                        : overrideEntry.itemKey().value();

        final ItemCategory category = overrideEntry.category() != null
                ? overrideEntry.category()
                : baseEntry != null
                        ? baseEntry.category()
                        : ItemCategory.MISC;

        final GeneratedItemCategory generatedCategory = overrideEntry.generatedCategory() != null
                ? overrideEntry.generatedCategory()
                : baseEntry != null
                        ? baseEntry.generatedCategory()
                        : GeneratedItemCategory.MISC;

        final ItemPolicyMode policyMode = overrideEntry.policyMode() != null
                ? overrideEntry.policyMode()
                : baseEntry != null
                        ? baseEntry.policyMode()
                        : ItemPolicyMode.DISABLED;

        final boolean buyEnabled = overrideEntry.buyEnabled() != null
                ? overrideEntry.buyEnabled()
                : baseEntry != null && baseEntry.buyEnabled();

        final boolean sellEnabled = overrideEntry.sellEnabled() != null
                ? overrideEntry.sellEnabled()
                : baseEntry != null && baseEntry.sellEnabled();

        final long stockCap = this.resolveLong(
                overrideEntry.stockCap(),
                baseEntry != null ? baseEntry.stockCap() : null
        );
        final long turnoverAmountPerInterval = this.resolveLong(
                overrideEntry.turnoverAmountPerInterval(),
                baseEntry != null ? baseEntry.turnoverAmountPerInterval() : null
        );

        return new ExchangeCatalogEntry(
                overrideEntry.itemKey(),
                displayName,
                category,
                generatedCategory,
                policyMode,
                baseWorth,
                buyPrice,
                sellPrice,
                sellEnvelope,
                stockCap,
                turnoverAmountPerInterval,
                buyEnabled,
                sellEnabled
        );
    }

    private BigDecimal resolveBaseWorth(
            final ExchangeCatalogEntry baseEntry,
            final BigDecimal rootValue
    ) {
        if (baseEntry != null && baseEntry.baseWorth() != null) {
            return this.scaleMoney(baseEntry.baseWorth());
        }
        if (rootValue != null) {
            return this.scaleMoney(rootValue);
        }
        return ZERO;
    }

    private BigDecimal resolveBuyPrice(
            final ExchangeItemsConfig.RawItemEntry overrideEntry,
            final ExchangeCatalogEntry baseEntry,
            final BigDecimal baseWorth,
            final BigDecimal rootValueFallback,
            final EcoEnvelopesConfig.EcoEnvelopeDefinition ecoEnvelope
    ) {
        if (overrideEntry.buyPrice() != null) {
            return this.scaleMoney(overrideEntry.buyPrice());
        }
        if (ecoEnvelope != null) {
            return this.scaleMoney(baseWorth.multiply(this.nonNegative(ecoEnvelope.buyPriceMultiplier())));
        }
        if (baseEntry != null && baseEntry.buyPrice() != null) {
            return this.scaleMoney(baseEntry.buyPrice());
        }
        if (rootValueFallback != null) {
            return this.scaleMoney(rootValueFallback);
        }
        return ZERO;
    }

    private BigDecimal resolveSellPrice(
            final ExchangeItemsConfig.RawItemEntry overrideEntry,
            final ExchangeCatalogEntry baseEntry,
            final BigDecimal baseWorth,
            final BigDecimal rootValueFallback,
            final EcoEnvelopesConfig.EcoEnvelopeDefinition ecoEnvelope
    ) {
        if (overrideEntry.sellPrice() != null) {
            return this.scaleMoney(overrideEntry.sellPrice());
        }
        if (ecoEnvelope != null) {
            return this.scaleMoney(baseWorth.multiply(this.nonNegative(ecoEnvelope.sellPriceMultiplier())));
        }
        if (baseEntry != null && baseEntry.sellPrice() != null) {
            return this.scaleMoney(baseEntry.sellPrice());
        }
        if (rootValueFallback != null) {
            return this.scaleMoney(rootValueFallback);
        }
        return ZERO;
    }

    private SellPriceBand resolveSellEnvelope(
            final ExchangeItemsConfig.RawItemEntry overrideEntry,
            final ExchangeCatalogEntry baseEntry,
            final EcoEnvelopesConfig.EcoEnvelopeDefinition ecoEnvelope,
            final BigDecimal resolvedSellPrice
    ) {
        if (ecoEnvelope != null) {
            return new SellPriceBand(
                    Math.max(0L, ecoEnvelope.minStock()),
                    Math.max(ecoEnvelope.minStock(), ecoEnvelope.maxStock()),
                    this.floorPriceFromFactor(resolvedSellPrice, ecoEnvelope.floorPriceFactor())
            );
        }
        if (baseEntry == null || baseEntry.sellEnvelope() == null) {
            return null;
        }

        final SellPriceBand baseEnvelope = baseEntry.sellEnvelope();
        final BigDecimal baseSellPrice = this.scaleMoney(baseEntry.sellPrice());
        final BigDecimal floorFactor;
        if (baseSellPrice.compareTo(BigDecimal.ZERO) <= 0) {
            floorFactor = BigDecimal.ONE;
        } else {
            floorFactor = this.scaleInternal(baseEnvelope.minUnitPrice())
                    .divide(this.scaleInternal(baseSellPrice), INTERNAL_SCALE, MONEY_ROUNDING);
        }

        return new SellPriceBand(
                Math.max(0L, baseEnvelope.minStockInclusive()),
                Math.max(baseEnvelope.minStockInclusive(), baseEnvelope.maxStockInclusive()),
                this.floorPriceFromFactor(resolvedSellPrice, floorFactor)
        );
    }

    private BigDecimal floorPriceFromFactor(final BigDecimal sellPrice, final BigDecimal factor) {
        final BigDecimal normalizedSellPrice = this.scaleMoney(sellPrice);
        final BigDecimal normalizedFactor = this.nonNegative(factor);
        final BigDecimal floorPrice = normalizedSellPrice.multiply(normalizedFactor);
        return this.clampFloorPrice(this.scaleMoney(floorPrice), normalizedSellPrice);
    }

    private BigDecimal clampFloorPrice(final BigDecimal floorPrice, final BigDecimal sellPrice) {
        if (floorPrice.compareTo(sellPrice) > 0) {
            return sellPrice;
        }
        return floorPrice;
    }

    private long resolveLong(final Long explicitValue, final Long baseValue) {
        if (explicitValue != null) {
            return Math.max(0L, explicitValue);
        }
        if (baseValue != null) {
            return Math.max(0L, baseValue);
        }
        return 0L;
    }

    private BigDecimal nonNegative(final BigDecimal value) {
        final BigDecimal normalized = this.scaleMoney(value == null ? BigDecimal.ZERO : value);
        return normalized.compareTo(BigDecimal.ZERO) < 0 ? ZERO : normalized;
    }

    private BigDecimal scaleMoney(final BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal scaleInternal(final BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(INTERNAL_SCALE, MONEY_ROUNDING);
        }
        return value.setScale(INTERNAL_SCALE, MONEY_ROUNDING);
    }
}

