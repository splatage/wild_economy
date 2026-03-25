package com.splatage.wild_economy.command;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.EconomyFormatter;
import com.splatage.wild_economy.economy.model.EconomyTransferResult;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.economy.service.EconomyService;
import java.math.BigDecimal;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PayCommand implements CommandExecutor {

    private final EconomyService economyService;
    private final EconomyConfig economyConfig;

    public PayCommand(
        final EconomyService economyService,
        final EconomyConfig economyConfig
    ) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /pay.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /pay <player> <amount>");
            return true;
        }

        final OfflinePlayer recipient = this.resolveKnownPlayer(args[0]);
        if (recipient == null) {
            sender.sendMessage("Unknown player: " + args[0]);
            return true;
        }
        if (recipient.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage("You cannot pay yourself.");
            return true;
        }

        final MoneyAmount amount;
        try {
            amount = MoneyAmount.fromMajor(new BigDecimal(args[1].trim()), this.economyConfig.fractionalDigits());
        } catch (final RuntimeException exception) {
            sender.sendMessage("Invalid amount: " + args[1]);
            return true;
        }

        if (!amount.isPositive()) {
            sender.sendMessage("Amount must be positive.");
            return true;
        }

        final EconomyTransferResult result = this.economyService.transfer(
                player.getUniqueId(),
                recipient.getUniqueId(),
                amount,
                "pay-command",
                player.getUniqueId().toString()
        );

        if (!result.success()) {
            sender.sendMessage(result.message());
            return true;
        }

        final String formattedAmount = EconomyFormatter.format(amount, this.economyConfig);
        final String recipientName = recipient.getName() == null ? recipient.getUniqueId().toString() : recipient.getName();

        player.sendMessage("Paid " + recipientName + " " + formattedAmount + ". New balance: "
                + EconomyFormatter.format(result.senderBalance(), this.economyConfig));

        if (recipient.isOnline()) {
            final Player onlineRecipient = recipient.getPlayer();
            if (onlineRecipient != null) {
                onlineRecipient.sendMessage("You received " + formattedAmount + " from " + player.getName()
                        + ". New balance: "
                        + EconomyFormatter.format(result.recipientBalance(), this.economyConfig));
            }
        }

        return true;
    }

    private OfflinePlayer resolveKnownPlayer(final String name) {
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.isOnline() && !offline.hasPlayedBefore()) {
            return null;
        }
        return offline;
    }
}
