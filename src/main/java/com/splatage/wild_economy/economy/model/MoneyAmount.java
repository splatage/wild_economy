package com.splatage.wild_economy.economy.model;

import java.math.BigDecimal;
import java.util.Objects;

public record MoneyAmount(long minorUnits) {

    public static MoneyAmount zero() {
        return new MoneyAmount(0L);
    }

    public static MoneyAmount ofMinor(final long minorUnits) {
        return new MoneyAmount(minorUnits);
    }

    public static MoneyAmount fromMajor(final BigDecimal majorAmount, final int fractionalDigits) {
        Objects.requireNonNull(majorAmount, "majorAmount");
        final BigDecimal scaled = majorAmount.movePointRight(fractionalDigits);
        try {
            return new MoneyAmount(scaled.longValueExact());
        } catch (final ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Amount '" + majorAmount + "' cannot be represented exactly with " + fractionalDigits + " fractional digits",
                    exception
            );
        }
    }

    public BigDecimal toMajor(final int fractionalDigits) {
        return BigDecimal.valueOf(this.minorUnits).movePointLeft(fractionalDigits);
    }

    public MoneyAmount add(final MoneyAmount other) {
        return new MoneyAmount(Math.addExact(this.minorUnits, other.minorUnits));
    }

    public MoneyAmount subtract(final MoneyAmount other) {
        return new MoneyAmount(Math.subtractExact(this.minorUnits, other.minorUnits));
    }

    public boolean isNegative() {
        return this.minorUnits < 0L;
    }

    public boolean isZero() {
        return this.minorUnits == 0L;
    }

    public boolean isPositive() {
        return this.minorUnits > 0L;
    }
}
