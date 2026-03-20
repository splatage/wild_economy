package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellLineResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellContainerSubcommand implements CommandExecutor {

    private final ExchangeService exchangeService;

    public ShopSellContainerSubcommand(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        final SellContainerResult result = this.exchangeService.sellContainer(player.getUniqueId());
        player.sendMessage(result.message());
        this.sendSoldLines(player, result.soldLines());
        this.sendSkippedLines(player, result.skippedDescriptions());
        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender);
    }

    private void sendSoldLines(final Player player, final List<SellLineResult> soldLines) {
        if (soldLines.isEmpty()) {
            return;
        }

        final int maxLines = Math.min(5, soldLines.size());
        for (int i = 0; i < maxLines; i++) {
            final SellLineResult line = soldLines.get(i);
            final String taperSuffix = line.tapered() ? " (reduced)" : "";
            player.sendMessage(" - " + line.amountSold() + "x " + line.displayName() + " for " + line.totalEarned() + taperSuffix);
        }

        if (soldLines.size() > maxLines) {
            player.sendMessage(" - ... and " + (soldLines.size() - maxLines) + " more stack(s)");
        }
    }

    private void sendSkippedLines(final Player player, final List<String> skippedDescriptions) {
        if (skippedDescriptions.isEmpty()) {
            return;
        }

        final int maxSkipped = Math.min(5, skippedDescriptions.size());
        player.sendMessage("Skipped:");
        for (int i = 0; i < maxSkipped; i++) {
            player.sendMessage(" - " + skippedDescriptions.get(i));
        }

        if (skippedDescriptions.size() > maxSkipped) {
            player.sendMessage(" - ... and " + (skippedDescriptions.size() - maxSkipped) + " more skipped stack(s)");
        }
    }
}
