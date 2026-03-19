package com.splatage.wild_economy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        // Route to open, sellhand, sellall
        return true;
    }
}
package com.splatage.wild_economy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        // Route to open, sellhand, sellall.
        return true;
    }
}
package com.splatage.wild_economy.command;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopCommand implements CommandExecutor {

    private final ShopSellHandSubcommand sellHandSubcommand;
    private final ShopSellAllSubcommand sellAllSubcommand;

    public ShopCommand(
        final ShopSellHandSubcommand sellHandSubcommand,
        final ShopSellAllSubcommand sellAllSubcommand
    ) {
        this.sellHandSubcommand = Objects.requireNonNull(sellHandSubcommand, "sellHandSubcommand");
        this.sellAllSubcommand = Objects.requireNonNull(sellAllSubcommand, "sellAllSubcommand");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Shop GUI coming soon. Use /shop sellhand or /shop sellall.");
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "sellhand" -> this.sellHandSubcommand.execute(sender);
            case "sellall" -> this.sellAllSubcommand.execute(sender);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /shop sellhand or /shop sellall.");
                yield true;
            }
        };
    }
}
