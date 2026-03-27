package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellAllSubcommand implements CommandExecutor {

    private final ExchangeService exchangeService;
    private final PlatformExecutor platformExecutor;

    public ShopSellAllSubcommand(final ExchangeService exchangeService, final PlatformExecutor platformExecutor) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        this.platformExecutor.runOnPlayer(player, () -> {
            final SellAllResult result = this.exchangeService.sellAll(player.getUniqueId());
            player.sendMessage(result.message());

            if (!result.soldLines().isEmpty()) {
                final int maxLines = Math.min(5, result.soldLines().size());
                for (int i = 0; i < maxLines; i++) {
                    final var line = result.soldLines().get(i);
                    final String taperSuffix = line.tapered() ? " (reduced)" : "";
                    player.sendMessage(
                        " - " + line.amountSold() + "x " + line.displayName() + " for " + line.totalEarned() + taperSuffix
                    );
                }

                if (result.soldLines().size() > maxLines) {
                    player.sendMessage(" - ... and " + (result.soldLines().size() - maxLines) + " more item type(s)");
                }
            }

            if (!result.skippedDescriptions().isEmpty()) {
                final int maxSkipped = Math.min(5, result.skippedDescriptions().size());
                player.sendMessage("Skipped:");
                for (int i = 0; i < maxSkipped; i++) {
                    player.sendMessage(" - " + result.skippedDescriptions().get(i));
                }

                if (result.skippedDescriptions().size() > maxSkipped) {
                    player.sendMessage(
                        " - ... and " + (result.skippedDescriptions().size() - maxSkipped) + " more skipped entries"
                    );
                }
            }
        });

        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender);
    }
}
