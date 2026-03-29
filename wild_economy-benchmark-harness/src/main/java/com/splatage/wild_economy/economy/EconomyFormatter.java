package com.splatage.wild_economy.economy;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class EconomyFormatter {

    private EconomyFormatter() {
    }

    public static String format(final MoneyAmount amount, final EconomyConfig config) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(config, "config");

        final BigDecimal major = amount.toMajor(config.fractionalDigits())
                .setScale(config.fractionalDigits(), RoundingMode.DOWN);
        final String number = major.toPlainString();

        if (config.useSymbolInFormatting()) {
            return config.currencySymbol() + number;
        }

        final String unit = major.compareTo(BigDecimal.ONE) == 0
                ? config.currencySingular()
                : config.currencyPlural();
        return number + " " + unit;
    }
}
