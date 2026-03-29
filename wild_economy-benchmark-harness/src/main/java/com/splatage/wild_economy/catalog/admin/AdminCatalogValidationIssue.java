package com.splatage.wild_economy.catalog.admin;

public record AdminCatalogValidationIssue(
    Severity severity,
    String message
) {
    public enum Severity {
        WARNING,
        ERROR
    }
}
