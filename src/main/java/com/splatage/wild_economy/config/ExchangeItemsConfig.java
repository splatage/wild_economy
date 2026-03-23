package com.splatage.wild_economy.config;

import com.splatage.wild_economy.exchange.domain.GeneratedItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public final class ExchangeItemsConfig {

    private final Map<ItemKey, RawItemEntry> items;

    public ExchangeItemsConfig(final Map<ItemKey, RawItemEntry> items) {
        this.items = Map.copyOf(items);
    }

    public Map<ItemKey, RawItemEntry> items() {
        return this.items;
    }

    public Optional<RawItemEntry> get(final ItemKey itemKey) {
        return Optional.ofNullable(this.items.get(itemKey));
    }

    public record RawItemEntry(
            ItemKey itemKey,
            String displayName,
            ItemCategory category,
            GeneratedItemCategory generatedCategory,
            ItemPolicyMode policyMode,
            Boolean buyEnabled,
            Boolean sellEnabled,
            Long stockCap,
            Long turnoverAmountPerInterval,
            String ecoEnvelopeKey,
            BigDecimal buyPrice,
            BigDecimal sellPrice
    ) {
    }
}
