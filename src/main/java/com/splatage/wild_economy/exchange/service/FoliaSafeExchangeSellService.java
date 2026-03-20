package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class FoliaSafeExchangeSellService implements ExchangeSellService {

    private static final String OFF_THREAD_MESSAGE =
        "Sell action was attempted off the owning player thread. Please try again.";

    private static final String CROSS_REGION_CONTAINER_MESSAGE =
        "Container selling is not available from this execution context. Move away from region borders and try again.";

    private final ExchangeSellService delegate;

    public FoliaSafeExchangeSellService(final ExchangeSellService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public SellHandResult sellHand(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, OFF_THREAD_MESSAGE);
        }

        return this.delegate.sellHand(playerId);
    }

    @Override
    public SellAllResult sellAll(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.of(), OFF_THREAD_MESSAGE);
        }

        return this.delegate.sellAll(playerId);
    }

    @Override
    public SellContainerResult sellContainer(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), null, "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), null, OFF_THREAD_MESSAGE);
        }

        if (!Bukkit.isOwnedByCurrentRegion(player.getLocation(), 1)) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                null,
                CROSS_REGION_CONTAINER_MESSAGE
            );
        }

        return this.delegate.sellContainer(playerId);
    }
}
