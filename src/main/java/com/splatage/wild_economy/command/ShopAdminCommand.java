package com.splatage.wild_economy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopAdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        // Route to reload, stock, turnover, quote.
        return true;
    }
}
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.WildEconomyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopAdminCommand implements CommandExecutor {

    private final WildEconomyPlugin plugin;

    public ShopAdminCommand(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Use /shopadmin reload");
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            this.plugin.reloadConfig();
            sender.sendMessage("wild_economy config.yml reloaded. Full runtime reload wiring still pending.");
            return true;
        }

        sender.sendMessage("Unknown admin subcommand.");
        return true;
    }
}
