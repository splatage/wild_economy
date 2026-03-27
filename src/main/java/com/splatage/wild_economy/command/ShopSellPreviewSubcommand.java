package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellPreviewLine;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import com.splatage.wild_economy.exchange.domain.StockState;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellPreviewSubcommand implements CommandExecutor {

    private static final int MAX_LINES = 8;

    private final ExchangeService exchangeService;
    private final PlatformExecutor platformExecutor;

    public ShopSellPreviewSubcommand(final ExchangeService exchangeService, final PlatformExecutor platformExecutor) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
    }

    public boolean execute(final CommandSender sender) {
        return this.execute(sender, new String[0]);
    }

    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        final boolean containerPreview = args.length >= 1 && args[0].toLowerCase(Locale.ROOT).equals("container");

        this.platformExecutor.runOnPlayer(player, () -> {
            final SellPreviewResult result = containerPreview
                ? this.exchangeService.previewContainerSell(player.getUniqueId())
                : this.exchangeService.previewInventorySell(player.getUniqueId());
            this.sendPreview(player, result);
        });

        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender, args);
    }

    private void sendPreview(final Player player, final SellPreviewResult result) {
        player.sendMessage(ChatColor.GOLD + result.message());
        this.sendPreviewLines(player, result.lines());
        this.sendSkippedLines(player, result.skippedDescriptions());
        if (!result.lines().isEmpty()) {
            player.sendMessage(
                ChatColor.GOLD + "Total quoted payout: " + ChatColor.GREEN + result.totalQuoted().toPlainString()
            );
        }
    }

    private void sendPreviewLines(final Player player, final List<SellPreviewLine> lines) {
        if (lines.isEmpty()) {
            return;
        }

        final int maxLines = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < maxLines; i++) {
            final SellPreviewLine line = lines.get(i);
            final String taperSuffix = line.tapered() ? ChatColor.GOLD + " (reduced)" : "";
            player.sendMessage(
                ChatColor.DARK_GRAY + " - "
                    + ChatColor.AQUA + line.amountQuoted() + "x " + line.displayName()
                    + ChatColor.DARK_GRAY + " -> "
                    + ChatColor.GREEN + line.totalQuoted().toPlainString()
                    + ChatColor.DARK_GRAY + " ["
                    + this.stockStateColor(line.stockState()) + this.stockStateLabel(line.stockState())
                    + ChatColor.DARK_GRAY + "]"
                    + taperSuffix
            );
        }

        if (lines.size() > maxLines) {
            player.sendMessage(
                ChatColor.DARK_GRAY + " - ... and " + ChatColor.GRAY + (lines.size() - maxLines) + " more item type(s)"
            );
        }
    }

    private void sendSkippedLines(final Player player, final List<String> skippedDescriptions) {
        if (skippedDescriptions.isEmpty()) {
            return;
        }

        final int maxSkipped = Math.min(MAX_LINES, skippedDescriptions.size());
        player.sendMessage(ChatColor.RED + "Skipped:");
        for (int i = 0; i < maxSkipped; i++) {
            player.sendMessage(ChatColor.DARK_RED + " - " + ChatColor.RED + skippedDescriptions.get(i));
        }

        if (skippedDescriptions.size() > maxSkipped) {
            player.sendMessage(
                ChatColor.DARK_RED + " - ... and " + ChatColor.RED + (skippedDescriptions.size() - maxSkipped) + " more skipped entries"
            );
        }
    }

    private ChatColor stockStateColor(final StockState stockState) {
        return switch (stockState) {
            case LOW -> ChatColor.GREEN;
            case HEALTHY -> ChatColor.YELLOW;
            case HIGH -> ChatColor.GOLD;
            case SATURATED, OUT_OF_STOCK -> ChatColor.RED;
        };
    }

    private String stockStateLabel(final StockState stockState) {
        return switch (stockState) {
            case HEALTHY -> "NORMAL";
            default -> stockState.name();
        };
    }
}
