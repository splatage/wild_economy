public record SellPreviewResult(
    boolean success,
    List<SellPreviewLine> lines,
    BigDecimal totalQuoted,
    List<String> skippedDescriptions,
    String message
) {}
