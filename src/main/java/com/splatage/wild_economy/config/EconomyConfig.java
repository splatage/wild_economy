package com.splatage.wild_economy.config;

public record EconomyConfig(
    String currencySingular,
    String currencyPlural,
    String currencySymbol,
    int fractionalDigits,
    boolean useSymbolInFormatting,
    boolean autoCreateOnJoin,
    boolean autoCreateOnFirstTransaction,
    int onlineRefreshSeconds,
    int offlineCacheSeconds,
    boolean refreshOnJoin,
    boolean refreshBeforeSensitiveOperations,
    boolean logBalanceAdjustments
) {}
