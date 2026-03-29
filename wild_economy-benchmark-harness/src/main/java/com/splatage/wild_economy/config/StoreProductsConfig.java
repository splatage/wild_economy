package com.splatage.wild_economy.config;

import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import java.util.Map;
import java.util.Objects;

public record StoreProductsConfig(
    Map<String, StoreCategory> categories,
    Map<String, StoreProduct> products
) {
    public StoreProductsConfig {
        categories = Map.copyOf(Objects.requireNonNull(categories, "categories"));
        products = Map.copyOf(Objects.requireNonNull(products, "products"));
    }

    public StoreCategory category(final String categoryId) {
        return this.categories.get(categoryId);
    }

    public StoreProduct product(final String productId) {
        return this.products.get(productId);
    }
}
