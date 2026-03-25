package com.splatage.wild_economy.economy;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.economy.service.EconomyService;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class InternalEconomyGateway implements EconomyGateway {

    private final EconomyService economyService;
    private final EconomyConfig economyConfig;

    public InternalEconomyGateway(
        final EconomyService economyService,
        final EconomyConfig economyConfig
    ) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    @Override
    public EconomyResult deposit(final UUID playerId, final BigDecimal amount) {
        final MoneyAmount moneyAmount;
        try {
            moneyAmount = this.toMoneyAmount(amount);
        } catch (final IllegalArgumentException exception) {
            return new EconomyResult(false, exception.getMessage());
        }

        final EconomyMutationResult result = this.economyService.deposit(
                playerId,
                moneyAmount,
                EconomyReason.EXCHANGE_SELL,
                "exchange",
                null
        );
        return new EconomyResult(result.success(), result.message() == null ? "" : result.message());
    }

    @Override
    public EconomyResult withdraw(final UUID playerId, final BigDecimal amount) {
        final MoneyAmount moneyAmount;
        try {
            moneyAmount = this.toMoneyAmount(amount);
        } catch (final IllegalArgumentException exception) {
            return new EconomyResult(false, exception.getMessage());
        }

        final EconomyMutationResult result = this.economyService.withdraw(
                playerId,
                moneyAmount,
                EconomyReason.EXCHANGE_BUY,
                "exchange",
                null
        );
        return new EconomyResult(result.success(), result.message() == null ? "" : result.message());
    }

    @Override
    public BigDecimal getBalance(final UUID playerId) {
        return this.economyService
                .getBalanceForSensitiveOperation(playerId)
                .toMajor(this.economyConfig.fractionalDigits());
    }

    private MoneyAmount toMoneyAmount(final BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return MoneyAmount.fromMajor(amount, this.economyConfig.fractionalDigits());
    }
}
