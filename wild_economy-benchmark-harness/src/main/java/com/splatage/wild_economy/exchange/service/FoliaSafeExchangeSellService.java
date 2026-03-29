package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;

public final class FoliaSafeExchangeSellService implements ExchangeSellService {

    private static final int CONTAINER_TARGET_RANGE = 5;

    private static final String OFF_THREAD_MESSAGE =
        "Sell action was attempted off the owning player thread. Please try again.";

    private static final String CROSS_REGION_CONTAINER_MESSAGE =
        "Looked-at container is outside the current owned region. Move closer and try again.";

    private final ExchangeSellService delegate;

    public FoliaSafeExchangeSellService(final ExchangeSellService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public SellHandResult sellHand(final Player player) {
        if (player == null || !player.isOnline()) {
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellHandResult(false, null, RejectionReason.INTERNAL_ERROR, OFF_THREAD_MESSAGE);
        }

        return this.delegate.sellHand(player);
    }

    @Override
    public SellAllResult sellAll(final Player player) {
        if (player == null || !player.isOnline()) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellAllResult(false, List.of(), BigDecimal.ZERO, List.of(), OFF_THREAD_MESSAGE);
        }

        return this.delegate.sellAll(player);
    }

    @Override
    public SellContainerResult sellContainer(final Player player) {
        if (player == null || !player.isOnline()) {
            return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), null, "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellContainerResult(false, List.of(), BigDecimal.ZERO, List.of(), null, OFF_THREAD_MESSAGE);
        }

        final Block targetBlock = player.getTargetBlockExact(CONTAINER_TARGET_RANGE);
        if (this.isSupportedContainerTarget(targetBlock) && !Bukkit.isOwnedByCurrentRegion(targetBlock.getLocation())) {
            return new SellContainerResult(
                false,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                null,
                CROSS_REGION_CONTAINER_MESSAGE
            );
        }

        return this.delegate.sellContainer(player);
    }

    @Override
    public SellPreviewResult previewInventorySell(final Player player) {
        if (player == null || !player.isOnline()) {
            return new SellPreviewResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellPreviewResult(false, List.of(), BigDecimal.ZERO, List.of(), OFF_THREAD_MESSAGE);
        }

        return this.delegate.previewInventorySell(player);
    }


    @Override
    public SellPreviewResult previewContainerSell(final Player player) {
        if (player == null || !player.isOnline()) {
            return new SellPreviewResult(false, List.of(), BigDecimal.ZERO, List.of(), "Player is not online");
        }

        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return new SellPreviewResult(false, List.of(), BigDecimal.ZERO, List.of(), OFF_THREAD_MESSAGE);
        }

        final Block targetBlock = player.getTargetBlockExact(CONTAINER_TARGET_RANGE);
        if (this.isSupportedContainerTarget(targetBlock) && !Bukkit.isOwnedByCurrentRegion(targetBlock.getLocation())) {
            return new SellPreviewResult(false, List.of(), BigDecimal.ZERO, List.of(), CROSS_REGION_CONTAINER_MESSAGE);
        }

        return this.delegate.previewContainerSell(player);
    }

    private boolean isSupportedContainerTarget(final Block targetBlock) {
        if (targetBlock == null) {
            return false;
        }

        final BlockState state = targetBlock.getState();
        return state instanceof Chest || state instanceof Barrel || state instanceof ShulkerBox;
    }
}
