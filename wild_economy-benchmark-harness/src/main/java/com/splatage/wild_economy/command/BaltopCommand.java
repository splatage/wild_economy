package com.splatage.wild_economy.command;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.EconomyFormatter;
import com.splatage.wild_economy.economy.model.BalanceRankEntry;
import com.splatage.wild_economy.economy.service.BaltopService;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class BaltopCommand implements CommandExecutor {

    private static final int PAGE_SIZE = 10;

    private final BaltopService baltopService;
    private final EconomyConfig economyConfig;

    public BaltopCommand(
        final BaltopService baltopService,
        final EconomyConfig economyConfig
    ) {
        this.baltopService = Objects.requireNonNull(baltopService, "baltopService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        int page = 1;
        if (args.length > 1) {
            sender.sendMessage("Usage: /baltop [page]");
            return true;
        }
        if (args.length == 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (final NumberFormatException exception) {
                sender.sendMessage("Invalid page: " + args[0]);
                return true;
            }
        }

        final List<BalanceRankEntry> entries = this.baltopService.getPage(page, PAGE_SIZE);
        if (entries.isEmpty()) {
            sender.sendMessage("No baltop entries found for page " + page + ".");
            return true;
        }

        sender.sendMessage("Top balances — page " + page);
        for (final BalanceRankEntry entry : entries) {
            final String displayName = entry.displayName() == null || entry.displayName().isBlank()
                    ? entry.playerId().toString()
                    : entry.displayName();
            sender.sendMessage("#" + entry.rank() + " " + displayName + " — "
                    + EconomyFormatter.format(entry.balance(), this.economyConfig));
        }
        return true;
    }
}
