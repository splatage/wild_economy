package com.splatage.wild_economy.command;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopCommand implements CommandExecutor {

    private final ShopOpenSubcommand openSubcommand;
    private final ShopSellHandSubcommand sellHandSubcommand;
    private final ShopSellAllSubcommand sellAllSubcommand;
    private final ShopSellContainerSubcommand sellContainerSubcommand;

    public ShopCommand(
        final ShopOpenSubcommand openSubcommand,
        final ShopSellHandSubcommand sellHandSubcommand,
        final ShopSellAllSubcommand sellAllSubcommand,
        final ShopSellContainerSubcommand sellContainerSubcommand
    ) {
        this.openSubcommand = Objects.requireNonNull(openSubcommand, "openSubcommand");
        this.sellHandSubcommand = Objects.requireNonNull(sellHandSubcommand, "sellHandSubcommand");
        this.sellAllSubcommand = Objects.requireNonNull(sellAllSubcommand, "sellAllSubcommand");
        this.sellContainerSubcommand = Objects.requireNonNull(sellContainerSubcommand, "sellContainerSubcommand");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            return this.openSubcommand.execute(sender);
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "sellhand" -> this.sellHandSubcommand.execute(sender);
            case "sellall" -> this.sellAllSubcommand.execute(sender);
            case "sellcontainer" -> this.sellContainerSubcommand.execute(sender);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /shop, /shop sellhand, /shop sellall, /shop sellcontainer, /sellhand, /sellall, or /sellcontainer.");
                yield true;
            }
        };
    }
}
