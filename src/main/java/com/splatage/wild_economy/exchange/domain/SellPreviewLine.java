public record SellPreviewLine(
    ItemKey itemKey,
    String displayName,
    int amountQuoted,
    BigDecimal effectiveUnitPrice,
    BigDecimal totalQuoted,
    StockState stockState,
    boolean tapered
) {}
