package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import org.bukkit.inventory.ItemStack;

public final class ItemValidationServiceImpl implements ItemValidationService {

    @Override
    public ValidationResult validateForSell(final ItemStack itemStack) {
        return new ValidationResult(false, null, RejectionReason.INTERNAL_ERROR, "Not implemented");
    }

    @Override
    public ValidationResult validateForBuy(final ItemKey itemKey) {
        return new ValidationResult(false, itemKey, RejectionReason.INTERNAL_ERROR, "Not implemented");
    }
}
package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.ItemPolicyMode;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class ItemValidationServiceImpl implements ItemValidationService {

    private final ItemNormalizer itemNormalizer;
    private final ExchangeCatalog exchangeCatalog;

    public ItemValidationServiceImpl(final ItemNormalizer itemNormalizer, final ExchangeCatalog exchangeCatalog) {
        this.itemNormalizer = itemNormalizer;
        this.exchangeCatalog = exchangeCatalog;
    }

    @Override
    public ValidationResult validateForSell(final ItemStack itemStack) {
        if (itemStack == null) {
            return new ValidationResult(false, null, RejectionReason.ITEM_NOT_ELIGIBLE, "Item is null");
        }

        final Optional<ItemKey> normalized = this.itemNormalizer.normalizeForExchange(itemStack);
        if (normalized.isEmpty()) {
            return new ValidationResult(false, null, RejectionReason.INVALID_ITEM_STATE, "Item is not canonical for Exchange");
        }

        final ItemKey itemKey = normalized.get();
        final Optional<ExchangeCatalogEntry> entryOptional = this.exchangeCatalog.get(itemKey);
        if (entryOptional.isEmpty()) {
            return new ValidationResult(false, itemKey, RejectionReason.ITEM_NOT_ELIGIBLE, "Item is not in the Exchange catalog");
        }

        final ExchangeCatalogEntry entry = entryOptional.get();
        if (entry.policyMode() == ItemPolicyMode.DISABLED) {
            return new ValidationResult(false, itemKey, RejectionReason.ITEM_DISABLED, "Item is disabled");
        }
        if (!entry.sellEnabled()) {
            return new ValidationResult(false, itemKey, RejectionReason.SELL_NOT_ALLOWED, "Item is not sellable");
        }

        return new ValidationResult(true, itemKey, null, null);
    }

    @Override
    public ValidationResult validateForBuy(final ItemKey itemKey) {
        final Optional<ExchangeCatalogEntry> entryOptional = this.exchangeCatalog.get(itemKey);
        if (entryOptional.isEmpty()) {
            return new ValidationResult(false, itemKey, RejectionReason.ITEM_NOT_ELIGIBLE, "Item is not in the Exchange catalog");
        }

        final ExchangeCatalogEntry entry = entryOptional.get();
        if (entry.policyMode() == ItemPolicyMode.DISABLED) {
            return new ValidationResult(false, itemKey, RejectionReason.ITEM_DISABLED, "Item is disabled");
        }
        if (!entry.buyEnabled()) {
            return new ValidationResult(false, itemKey, RejectionReason.BUY_NOT_ALLOWED, "Item is not buyable");
        }

        return new ValidationResult(true, itemKey, null, null);
    }
}
