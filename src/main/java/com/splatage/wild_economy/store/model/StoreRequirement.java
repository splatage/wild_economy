package com.splatage.wild_economy.store.model;

import java.util.Objects;

public record StoreRequirement(
    StoreRequirementType type,
    String key,
    String permissionNode,
    String statistic,
    String material,
    long minimum
) {
    public StoreRequirement {
        Objects.requireNonNull(type, "type");
        if (minimum < 0L) {
            throw new IllegalArgumentException("minimum cannot be negative");
        }
        switch (type) {
            case ENTITLEMENT -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException("ENTITLEMENT requirements require a key");
                }
            }
            case PERMISSION -> {
                if (permissionNode == null || permissionNode.isBlank()) {
                    throw new IllegalArgumentException("PERMISSION requirements require a permission node");
                }
            }
            case STATISTIC -> {
                if (statistic == null || statistic.isBlank()) {
                    throw new IllegalArgumentException("STATISTIC requirements require a statistic");
                }
            }
            case STATISTIC_MATERIAL -> {
                if (statistic == null || statistic.isBlank()) {
                    throw new IllegalArgumentException("STATISTIC_MATERIAL requirements require a statistic");
                }
                if (material == null || material.isBlank()) {
                    throw new IllegalArgumentException("STATISTIC_MATERIAL requirements require a material");
                }
            }
        }
    }
}
