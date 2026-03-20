package com.splatage.wild_economy.integration.protection;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class AllowAllContainerAccessService implements ContainerAccessService {

    @Override
    public ContainerAccessResult canAccessPlacedContainer(final Player player, final Block targetBlock) {
        return ContainerAccessResult.allow();
    }
}
