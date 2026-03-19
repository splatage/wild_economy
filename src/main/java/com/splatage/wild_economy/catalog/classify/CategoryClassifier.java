package com.splatage.wild_economy.catalog.classify;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;

public interface CategoryClassifier {
    CatalogCategory classify(ItemFacts facts);
}
