package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import java.math.BigDecimal;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class FoliaSafeExchangeBuyService implements ExchangeBuyService {

    private final ExchangeBuyService delegate;

    public FoliaSafeExchangeBuyService(final ExchangeBuyService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public BuyResult buy(final Player player, final ItemKey itemKey, final int amount) {
        if (player == null || !player.isOnline()) {
            return new BuyResult(
                false,
                itemKey,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                RejectionReason.INTERNAL_ERROR,
                "Player is not online"
            );
        }
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new BuyResult(
                false,
                itemKey,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                RejectionReason.INTERNAL_ERROR,
                "Buy action was attempted off the owning player thread. Please try again."
            );
        }

        return this.delegate.buy(player, itemKey, amount);
    }

    @Override
    public BuyResult buyQuoted(
        final Player player,
        final ItemKey itemKey,
        final int amount,
        final BigDecimal quotedUnitPrice
    ) {
        if (player == null || !player.isOnline()) {
            return new BuyResult(
                false,
                itemKey,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                RejectionReason.INTERNAL_ERROR,
                "Player is not online"
            );
        }
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new BuyResult(
                false,
                itemKey,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                RejectionReason.INTERNAL_ERROR,
                "Buy action was attempted off the owning player thread. Please try again."
            );
        }

        return this.delegate.buyQuoted(player, itemKey, amount, quotedUnitPrice);
    }
}
