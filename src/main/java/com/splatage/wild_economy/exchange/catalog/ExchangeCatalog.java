package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ExchangeCatalog {

    private final Map<ItemKey, ExchangeCatalogEntry> entries;

    public ExchangeCatalog(final Map<ItemKey, ExchangeCatalogEntry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public Optional<ExchangeCatalogEntry> get(final ItemKey itemKey) {
        return Optional.ofNullable(this.entries.get(itemKey));
    }

    public Collection<ExchangeCatalogEntry> allEntries() {
        return this.entries.values();
    }

    public List<ExchangeCatalogEntry> byCategory(final ItemCategory category) {
        return this.entries.values().stream()
            .filter(entry -> entry.category() == category)
            .collect(Collectors.toList());
    }
}
package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ExchangeCatalog {

    private final Map<ItemKey, ExchangeCatalogEntry> entries;

    public ExchangeCatalog(final Map<ItemKey, ExchangeCatalogEntry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public Optional<ExchangeCatalogEntry> get(final ItemKey itemKey) {
        return Optional.ofNullable(this.entries.get(itemKey));
    }

    public Collection<ExchangeCatalogEntry> allEntries() {
        return this.entries.values();
    }

    public List<ExchangeCatalogEntry> byCategory(final ItemCategory category) {
        return this.entries.values().stream()
            .filter(entry -> entry.category() == category)
            .collect(Collectors.toList());
    }
}
