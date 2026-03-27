package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellPreviewLine;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellPreviewSubcommand implements CommandExecutor {

    private final ExchangeService exchangeService;
    private final PlatformExecutor platformExecutor;

    public ShopSellPreviewSubcommand(final ExchangeService exchangeService, final PlatformExecutor platformExecutor) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        this.platformExecutor.runOnPlayer(player, () -> {
            final SellPreviewResult result = this.exchangeService.previewInventorySell(player.getUniqueId());
            player.sendMessage(result.message());
            this.sendPreviewLines(player, result.lines());
            this.sendSkippedLines(player, result.skippedDescriptions());
            if (!result.lines().isEmpty()) {
                player.sendMessage("Total quoted payout: " + result.totalQuoted());
            }
        });

        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender);
    }

    private void sendPreviewLines(final Player player, final List<SellPreviewLine> lines) {
        if (lines.isEmpty()) {
            return;
        }

        final int maxLines = Math.min(8, lines.size());
        for (int i = 0; i < maxLines; i++) {
            final SellPreviewLine line = lines.get(i);
            final String taperSuffix = line.tapered() ? " (reduced)" : "";
            player.sendMessage(
                " - "
                    + line.amountQuoted()
                    + "x "
                    + line.displayName()
                    + " -> "
                    + line.totalQuoted()
                    + " ["
                    + line.stockState().name()
                    + "]"
                    + taperSuffix
            );
        }

        if (lines.size() > maxLines) {
            player.sendMessage(" - ... and " + (lines.size() - maxLines) + " more item type(s)");
        }
    }

    private void sendSkippedLines(final Player player, final List<String> skippedDescriptions) {
        if (skippedDescriptions.isEmpty()) {
            return;
        }

        final int maxSkipped = Math.min(8, skippedDescriptions.size());
        player.sendMessage("Skipped:");
        for (int i = 0; i < maxSkipped; i++) {
            player.sendMessage(" - " + skippedDescriptions.get(i));
        }

        if (skippedDescriptions.size() > maxSkipped) {
            player.sendMessage(" - ... and " + (skippedDescriptions.size() - maxSkipped) + " more skipped entries");
        }
    }
}
