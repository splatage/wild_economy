package com.splatage.wild_economy.catalog.admin;

public record AdminCatalogDiffEntry(
    ChangeType changeType,
    String itemKey,
    String summary
) {
    public enum ChangeType {
        ADDED,
        REMOVED,
        CHANGED
    }
}
