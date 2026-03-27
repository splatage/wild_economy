package com.splatage.wild_economy.exchange.supplier;

public enum SupplierScope {
    WEEKLY("weekly"),
    ALL_TIME("alltime");

    private final String commandToken;

    SupplierScope(final String commandToken) {
        this.commandToken = commandToken;
    }

    public String commandToken() {
        return this.commandToken;
    }

    public static SupplierScope fromToken(final String token) {
        if (token == null || token.isBlank()) {
            return WEEKLY;
        }
        return switch (token.toLowerCase(java.util.Locale.ROOT)) {
            case "all", "alltime", "all-time" -> ALL_TIME;
            default -> WEEKLY;
        };
    }
}
