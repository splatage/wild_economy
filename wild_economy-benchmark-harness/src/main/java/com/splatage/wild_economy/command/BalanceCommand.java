package com.splatage.wild_economy.command;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.EconomyFormatter;
import com.splatage.wild_economy.economy.service.EconomyService;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BalanceCommand implements CommandExecutor {

    private final EconomyService economyService;
    private final EconomyConfig economyConfig;

    public BalanceCommand(
        final EconomyService economyService,
        final EconomyConfig economyConfig
    ) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Console must specify a player. Usage: /" + label + " <player>");
                return true;
            }
            sender.sendMessage("Your balance: " + EconomyFormatter.format(
                    this.economyService.getBalance(player.getUniqueId()),
                    this.economyConfig
            ));
            return true;
        }

        if (!sender.hasPermission("wild_economy.balance.others")) {
            sender.sendMessage("You do not have permission to view other players' balances.");
            return true;
        }

        final OfflinePlayer target = this.resolveKnownPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Unknown player: " + args[0]);
            return true;
        }

        final String displayName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        sender.sendMessage("Balance for " + displayName + ": " + EconomyFormatter.format(
                this.economyService.getBalance(target.getUniqueId()),
                this.economyConfig
        ));
        return true;
    }

    private OfflinePlayer resolveKnownPlayer(final String name) {
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.isOnline() && !offline.hasPlayedBefore()) {
            return null;
        }
        return offline;
    }
}
