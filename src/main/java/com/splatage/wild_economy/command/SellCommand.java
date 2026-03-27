package com.splatage.wild_economy.command;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class SellCommand implements CommandExecutor {

    private final ShopSellPreviewSubcommand sellPreviewSubcommand;

    public SellCommand(final ShopSellPreviewSubcommand sellPreviewSubcommand) {
        this.sellPreviewSubcommand = Objects.requireNonNull(sellPreviewSubcommand, "sellPreviewSubcommand");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /sell preview");
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "preview" -> this.sellPreviewSubcommand.execute(sender);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /sell preview.");
                yield true;
            }
        };
    }
}
