package com.splatage.wild_economy.store.model;

import java.util.Objects;

public record StoreRequirement(
    StoreRequirementType type,
    String key,
    String permissionNode,
    String statistic,
    String material,
    String entityType,
    long minimum
) {
    public StoreRequirement {
        Objects.requireNonNull(type, "type");
        if (minimum < 0L) {
            throw new IllegalArgumentException("minimum cannot be negative");
        }
        switch (type) {
            case ENTITLEMENT, ADVANCEMENT, CUSTOM_COUNTER -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException(type + " requirements require a key");
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
            case STATISTIC_ENTITY -> {
                if (statistic == null || statistic.isBlank()) {
                    throw new IllegalArgumentException("STATISTIC_ENTITY requirements require a statistic");
                }
                if (entityType == null || entityType.isBlank()) {
                    throw new IllegalArgumentException("STATISTIC_ENTITY requirements require an entity type");
                }
            }
        }
    }
}
