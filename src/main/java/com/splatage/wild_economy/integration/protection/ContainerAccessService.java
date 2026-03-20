package com.splatage.wild_economy.integration.protection;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

public interface ContainerAccessService {

    ContainerAccessResult canAccessPlacedContainer(Player player, Block targetBlock, BlockState blockState);
}
