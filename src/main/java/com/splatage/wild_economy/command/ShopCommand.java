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
    private final ShopTopSubcommand shopTopSubcommand;

    public ShopCommand(
        final ShopOpenSubcommand openSubcommand,
        final ShopSellHandSubcommand sellHandSubcommand,
        final ShopSellAllSubcommand sellAllSubcommand,
        final ShopSellContainerSubcommand sellContainerSubcommand,
        final ShopTopSubcommand shopTopSubcommand
    ) {
        this.openSubcommand = Objects.requireNonNull(openSubcommand, "openSubcommand");
        this.sellHandSubcommand = Objects.requireNonNull(sellHandSubcommand, "sellHandSubcommand");
        this.sellAllSubcommand = Objects.requireNonNull(sellAllSubcommand, "sellAllSubcommand");
        this.sellContainerSubcommand = Objects.requireNonNull(sellContainerSubcommand, "sellContainerSubcommand");
        this.shopTopSubcommand = Objects.requireNonNull(shopTopSubcommand, "shopTopSubcommand");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            return this.openSubcommand.execute(sender);
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("top")) {
            return this.shopTopSubcommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        }

        return switch (subcommand) {
            case "sellhand" -> this.sellHandSubcommand.execute(sender);
            case "sellall" -> this.sellAllSubcommand.execute(sender);
            case "sellcontainer" -> this.sellContainerSubcommand.execute(sender);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /shop, /shop sellhand, /shop sellall, /shop top, /shop sellcontainer, /sell preview, /worth, /sellhand, /sellall, /sellcontainer, or /shoptop.");
                yield true;
            }
        };
    }
}
