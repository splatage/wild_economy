package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.BuyResult;
import com.splatage.wild_economy.exchange.domain.RejectionReason;
import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.domain.SellContainerResult;
import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.domain.SellLineResult;
import com.splatage.wild_economy.exchange.domain.SellPreviewLine;
import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import com.splatage.wild_economy.exchange.domain.StockState;
import java.math.BigDecimal;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class ExchangeMessageFormatter {

    private static final int MAX_DETAIL_LINES = 5;
    private static final int MAX_PREVIEW_LINES = 8;

    private ExchangeMessageFormatter() {
    }

    public static void sendSellAll(final Player player, final SellAllResult result) {
        if (!result.success()) {
            sendSellFailure(player, result.message(), result.skippedDescriptions());
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Sell Complete");
        player.sendMessage(ChatColor.GRAY + " + " + ChatColor.GREEN + money(result.totalEarned()));
        sendSoldLines(player, result.soldLines(), MAX_DETAIL_LINES);
        sendSkippedLines(player, result.skippedDescriptions(), MAX_DETAIL_LINES);
        playSellSuccess(player);
    }

    public static void sendSellContainer(final Player player, final SellContainerResult result) {
        if (!result.success()) {
            sendSellFailure(player, result.message(), result.skippedDescriptions());
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Sell Complete");
        if (result.targetDescription() != null && !result.targetDescription().isBlank()) {
            player.sendMessage(ChatColor.GRAY + " Source: " + ChatColor.AQUA + result.targetDescription());
        }
        player.sendMessage(ChatColor.GRAY + " + " + ChatColor.GREEN + money(result.totalEarned()));
        sendSoldLines(player, result.soldLines(), MAX_DETAIL_LINES);
        sendSkippedLines(player, result.skippedDescriptions(), MAX_DETAIL_LINES);
        playSellSuccess(player);
    }

    public static void sendSellHand(final Player player, final SellHandResult result) {
        if (!result.success()) {
            sendSellFailure(player, result.message(), List.of());
            return;
        }

        final SellLineResult line = result.lineResult();
        player.sendMessage(ChatColor.GOLD + "Sell Complete");
        player.sendMessage(ChatColor.GRAY + " + " + ChatColor.GREEN + money(line.totalEarned()));
        player.sendMessage(ChatColor.GRAY + " Item sold:");
        player.sendMessage(ChatColor.DARK_GRAY + "   • "
            + ChatColor.AQUA + line.amountSold() + "x " + line.displayName()
            + ChatColor.DARK_GRAY + " → "
            + ChatColor.GREEN + money(line.totalEarned())
            + (line.tapered() ? ChatColor.GOLD + " (reduced)" : ""));
        playSellSuccess(player);
    }

    public static void sendSellPreview(final Player player, final SellPreviewResult result) {
        player.sendMessage(ChatColor.GOLD + result.message());
        sendPreviewLines(player, result.lines());
        sendSkippedLines(player, result.skippedDescriptions(), MAX_PREVIEW_LINES);
        if (!result.lines().isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "Total quoted payout: " + ChatColor.GREEN + money(result.totalQuoted()));
        }
    }

    public static void sendBuyResult(
        final Player player,
        final BuyResult result,
        final String displayName,
        final int requestedAmount
    ) {
        if (!result.success()) {
            sendBuyFailure(player, result, displayName);
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Purchase Complete");
        player.sendMessage(ChatColor.GRAY + " - " + ChatColor.RED + money(result.totalCost()));
        player.sendMessage(ChatColor.GRAY + " Items received:");
        player.sendMessage(ChatColor.DARK_GRAY + "   • " + ChatColor.AQUA + result.amountBought() + "x " + displayName);

        if (requestedAmount > result.amountBought()) {
            final int undelivered = requestedAmount - result.amountBought();
            player.sendMessage(
                ChatColor.GOLD + " Only " + result.amountBought() + " of " + requestedAmount
                    + " could be delivered. " + ChatColor.YELLOW + undelivered + " item(s) were refunded."
            );
        }

        playBuySuccess(player);
    }

    private static void sendSellFailure(
        final Player player,
        final String message,
        final List<String> skippedDescriptions
    ) {
        if (message != null && message.equalsIgnoreCase("No sellable items found")) {
            player.sendMessage(ChatColor.YELLOW + "Nothing to sell");
            player.sendMessage(ChatColor.GRAY + " No sellable items were found.");
        } else {
            player.sendMessage(ChatColor.RED + "Sell failed");
            if (message != null && !message.isBlank()) {
                player.sendMessage(ChatColor.GRAY + " " + message);
            }
        }

        if (!skippedDescriptions.isEmpty()) {
            sendSkippedLines(player, skippedDescriptions, MAX_DETAIL_LINES);
        }
        playFailure(player);
    }

    private static void sendBuyFailure(final Player player, final BuyResult result, final String displayName) {
        if (result.rejectionReason() == RejectionReason.INSUFFICIENT_FUNDS && result.totalCost() != null) {
            player.sendMessage(ChatColor.RED + "Not enough balance");
            player.sendMessage(ChatColor.GRAY + " Required: " + ChatColor.RED + money(result.totalCost()));
            playFailure(player);
            return;
        }

        if (result.rejectionReason() == RejectionReason.OUT_OF_STOCK) {
            player.sendMessage(ChatColor.YELLOW + "Out of stock");
            player.sendMessage(ChatColor.GRAY + " " + displayName + " is not available in that quantity right now.");
            playFailure(player);
            return;
        }

        if (result.rejectionReason() == RejectionReason.INVENTORY_FULL) {
            player.sendMessage(ChatColor.YELLOW + "Purchase could not be delivered");
            player.sendMessage(ChatColor.GRAY + " " + result.message());
            playFailure(player);
            return;
        }

        player.sendMessage(ChatColor.RED + "Purchase failed");
        if (result.message() != null && !result.message().isBlank()) {
            player.sendMessage(ChatColor.GRAY + " " + result.message());
        }
        playFailure(player);
    }

    private static void sendSoldLines(final Player player, final List<SellLineResult> soldLines, final int maxLines) {
        if (soldLines.isEmpty()) {
            return;
        }

        player.sendMessage(ChatColor.GRAY + " Items sold:");
        final int limit = Math.min(maxLines, soldLines.size());
        for (int i = 0; i < limit; i++) {
            final SellLineResult line = soldLines.get(i);
            player.sendMessage(ChatColor.DARK_GRAY + "   • "
                + ChatColor.AQUA + line.amountSold() + "x " + line.displayName()
                + ChatColor.DARK_GRAY + " → "
                + ChatColor.GREEN + money(line.totalEarned())
                + (line.tapered() ? ChatColor.GOLD + " (reduced)" : ""));
        }

        if (soldLines.size() > limit) {
            player.sendMessage(ChatColor.DARK_GRAY + "   • ... and " + ChatColor.GRAY + (soldLines.size() - limit) + " more item type(s)");
        }
    }

    private static void sendPreviewLines(final Player player, final List<SellPreviewLine> lines) {
        if (lines.isEmpty()) {
            return;
        }

        final int limit = Math.min(MAX_PREVIEW_LINES, lines.size());
        for (int i = 0; i < limit; i++) {
            final SellPreviewLine line = lines.get(i);
            final String taperSuffix = line.tapered() ? ChatColor.GOLD + " (reduced)" : "";
            player.sendMessage(
                ChatColor.DARK_GRAY + " • "
                    + ChatColor.AQUA + line.amountQuoted() + "x " + line.displayName()
                    + ChatColor.DARK_GRAY + " → "
                    + ChatColor.GREEN + money(line.totalQuoted())
                    + ChatColor.DARK_GRAY + " ["
                    + stockStateColor(line.stockState()) + stockStateLabel(line.stockState())
                    + ChatColor.DARK_GRAY + "]"
                    + taperSuffix
            );
        }

        if (lines.size() > limit) {
            player.sendMessage(ChatColor.DARK_GRAY + " • ... and " + ChatColor.GRAY + (lines.size() - limit) + " more item type(s)");
        }
    }

    private static void sendSkippedLines(final Player player, final List<String> skippedDescriptions, final int maxLines) {
        if (skippedDescriptions.isEmpty()) {
            return;
        }

        player.sendMessage(ChatColor.RED + "Skipped:");
        final int limit = Math.min(maxLines, skippedDescriptions.size());
        for (int i = 0; i < limit; i++) {
            player.sendMessage(ChatColor.DARK_RED + "   • " + ChatColor.RED + skippedDescriptions.get(i));
        }

        if (skippedDescriptions.size() > limit) {
            player.sendMessage(ChatColor.DARK_RED + "   • ... and " + ChatColor.RED + (skippedDescriptions.size() - limit) + " more skipped entries");
        }
    }

    private static ChatColor stockStateColor(final StockState stockState) {
        return switch (stockState) {
            case LOW -> ChatColor.GREEN;
            case HEALTHY -> ChatColor.YELLOW;
            case HIGH -> ChatColor.GOLD;
            case SATURATED, OUT_OF_STOCK -> ChatColor.RED;
        };
    }

    private static String stockStateLabel(final StockState stockState) {
        return switch (stockState) {
            case HEALTHY -> "NORMAL";
            default -> stockState.name();
        };
    }

    private static String money(final BigDecimal amount) {
        return amount == null ? "0.00" : amount.toPlainString();
    }

    private static void playSellSuccess(final Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.15f);
    }

    private static void playBuySuccess(final Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.05f);
    }

    private static void playFailure(final Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }
}
