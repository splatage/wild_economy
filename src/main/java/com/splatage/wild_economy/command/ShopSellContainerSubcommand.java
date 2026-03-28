package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.service.FoliaContainerSellCoordinator;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellContainerSubcommand implements CommandExecutor {

    private final FoliaContainerSellCoordinator sellCoordinator;

    public ShopSellContainerSubcommand(final FoliaContainerSellCoordinator sellCoordinator) {
        this.sellCoordinator = Objects.requireNonNull(sellCoordinator, "sellCoordinator");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        this.sellCoordinator.sellContainer(player, result -> ExchangeMessageFormatter.sendSellContainer(player, result));
        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender);
    }
}
