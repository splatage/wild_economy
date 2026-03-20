package com.splatage.wild_economy.exchange.service;

import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.integration.protection.ContainerAccessResult;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class FoliaContainerSellCoordinator {

    private static final int CONTAINER_TARGET_RANGE = 5;

    private final PlatformExecutor platformExecutor;
    private final ExchangeService exchangeService;
    private final ExchangeSellServiceImpl exchangeSellService;

    public FoliaContainerSellCoordinator(
        final PlatformExecutor platformExecutor,
        final ExchangeService exchangeService,
        final ExchangeSellServiceImpl exchangeSellService
    ) {
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.exchangeSellService = Objects.requireNonNull(exchangeSellService, "exchangeSellService");
    }

    public void sellContainer(final Player player, final Consumer<SellContainerResult> callback) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(callback, "callback");

        this.platformExecutor.runOnPlayer(player, () -> this.sellContainerOnPlayer(player, callback));
    }

    private void sellContainerOnPlayer(final Player player, final Consumer<SellContainerResult> callback) {
        final Block targetBlock = player.getTargetBlockExact(CONTAINER_TARGET_RANGE);
        if (!this.exchangeSellService.isSupportedPlacedContainerTarget(targetBlock)) {
            callback.accept(this.exchangeService.sellContainer(player.getUniqueId()));
            return;
        }

        final ContainerAccessResult accessResult = this.exchangeSellService.canAccessPlacedContainer(player, targetBlock);
        if (!accessResult.allowed()) {
            callback.accept(this.exchangeSellService.buildPlacedContainerDeniedResult(targetBlock, accessResult.message()));
            return;
        }

        final Location targetLocation = targetBlock.getLocation().clone();
        this.platformExecutor.runOnLocation(targetLocation, () -> {
            final SellContainerResult result = this.exchangeSellService.sellPlacedContainerAtLocation(
                player.getUniqueId(),
                targetLocation
            );
            this.platformExecutor.runOnPlayer(player, () -> callback.accept(result));
        });
    }
}
