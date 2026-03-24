package com.splatage.wild_economy.catalog.rootvalue;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import java.util.Optional;

@FunctionalInterface
public interface CategoryHintLookup {
    Optional<CatalogCategory> findCategoryHint(String itemKey);
}
