package com.splatage.wild_economy.config;

import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record StoreProductsConfig(
    Map<String, StoreCategory> categories,
    Map<String, StoreProduct> products
) {
    public static final StoreProductsConfig EMPTY = new StoreProductsConfig(Map.of(), Map.of());

    public StoreProductsConfig {
        categories = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(categories, "categories")));
        products = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(products, "products")));
    }

    public StoreCategory category(final String categoryId) {
        return this.categories.get(categoryId);
    }

    public StoreProduct product(final String productId) {
        return this.products.get(productId);
    }
}
