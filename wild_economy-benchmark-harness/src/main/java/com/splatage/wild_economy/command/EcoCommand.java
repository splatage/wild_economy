package com.splatage.wild_economy.command;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.EconomyFormatter;
import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.economy.service.EconomyService;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class EcoCommand implements CommandExecutor {

    private final EconomyService economyService;
    private final EconomyConfig economyConfig;

    public EcoCommand(
        final EconomyService economyService,
        final EconomyConfig economyConfig
    ) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length < 2) {
            this.sendUsage(sender, label);
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "balance" -> this.handleBalance(sender, label, args);
            case "give" -> this.handleGive(sender, label, args);
            case "take" -> this.handleTake(sender, label, args);
            case "set" -> this.handleSet(sender, label, args);
            case "reset" -> this.handleReset(sender, label, args);
            default -> {
                this.sendUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean handleBalance(final CommandSender sender, final String label, final String[] args) {
        if (!sender.hasPermission("wild_economy.admin.eco.balance")) {
            sender.sendMessage("You do not have permission to use /" + label + " balance.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " balance <player>");
            return true;
        }

        final OfflinePlayer target = this.resolveKnownPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Unknown player: " + args[1]);
            return true;
        }

        final String displayName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        sender.sendMessage("Balance for " + displayName + ": " + EconomyFormatter.format(
                this.economyService.getBalance(target.getUniqueId()),
                this.economyConfig
        ));
        return true;
    }

    private boolean handleGive(final CommandSender sender, final String label, final String[] args) {
        if (!sender.hasPermission("wild_economy.admin.eco.give")) {
            sender.sendMessage("You do not have permission to use /" + label + " give.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " give <player> <amount>");
            return true;
        }

        final OfflinePlayer target = this.resolveKnownPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Unknown player: " + args[1]);
            return true;
        }

        final MoneyAmount amount = this.parsePositiveAmount(sender, args[2]);
        if (amount == null) {
            return true;
        }

        final EconomyMutationResult result = this.economyService.deposit(
                target.getUniqueId(),
                amount,
                EconomyReason.ADMIN_GIVE,
                "eco-command",
                sender.getName()
        );
        return this.sendMutationResult(sender, target, amount, result, "gave");
    }

    private boolean handleTake(final CommandSender sender, final String label, final String[] args) {
        if (!sender.hasPermission("wild_economy.admin.eco.take")) {
            sender.sendMessage("You do not have permission to use /" + label + " take.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " take <player> <amount>");
            return true;
        }

        final OfflinePlayer target = this.resolveKnownPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Unknown player: " + args[1]);
            return true;
        }

        final MoneyAmount amount = this.parsePositiveAmount(sender, args[2]);
        if (amount == null) {
            return true;
        }

        final EconomyMutationResult result = this.economyService.withdraw(
                target.getUniqueId(),
                amount,
                EconomyReason.ADMIN_TAKE,
                "eco-command",
                sender.getName()
        );
        return this.sendMutationResult(sender, target, amount, result, "took");
    }

    private boolean handleSet(final CommandSender sender, final String label, final String[] args) {
        if (!sender.hasPermission("wild_economy.admin.eco.set")) {
            sender.sendMessage("You do not have permission to use /" + label + " set.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " set <player> <amount>");
            return true;
        }

        final OfflinePlayer target = this.resolveKnownPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Unknown player: " + args[1]);
            return true;
        }

        final MoneyAmount amount;
        try {
            amount = MoneyAmount.fromMajor(new BigDecimal(args[2].trim()), this.economyConfig.fractionalDigits());
        } catch (final RuntimeException exception) {
            sender.sendMessage("Invalid amount: " + args[2]);
            return true;
        }

        if (amount.isNegative()) {
            sender.sendMessage("Balance cannot be negative.");
            return true;
        }

        final EconomyMutationResult result = this.economyService.setBalance(
                target.getUniqueId(),
                amount,
                EconomyReason.ADMIN_SET,
                "eco-command",
                sender.getName()
        );

        if (!result.success()) {
            sender.sendMessage(result.message());
            return true;
        }

        final String displayName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        sender.sendMessage("Set " + displayName + "'s balance to "
                + EconomyFormatter.format(amount, this.economyConfig) + ".");
        return true;
    }

    private boolean handleReset(final CommandSender sender, final String label, final String[] args) {
        if (!sender.hasPermission("wild_economy.admin.eco.reset")) {
            sender.sendMessage("You do not have permission to use /" + label + " reset.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " reset <player>");
            return true;
        }

        final OfflinePlayer target = this.resolveKnownPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Unknown player: " + args[1]);
            return true;
        }

        final EconomyMutationResult result = this.economyService.setBalance(
                target.getUniqueId(),
                MoneyAmount.zero(),
                EconomyReason.ADMIN_SET,
                "eco-command",
                sender.getName()
        );

        if (!result.success()) {
            sender.sendMessage(result.message());
            return true;
        }

        final String displayName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        sender.sendMessage("Reset " + displayName + "'s balance to "
                + EconomyFormatter.format(MoneyAmount.zero(), this.economyConfig) + ".");
        return true;
    }

    private boolean sendMutationResult(
        final CommandSender sender,
        final OfflinePlayer target,
        final MoneyAmount amount,
        final EconomyMutationResult result,
        final String verb
    ) {
        if (!result.success()) {
            sender.sendMessage(result.message());
            return true;
        }

        final String displayName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        sender.sendMessage("Successfully " + verb + " "
                + EconomyFormatter.format(amount, this.economyConfig)
                + (verb.endsWith("e") ? " to " : " from ")
                + displayName
                + ". New balance: "
                + EconomyFormatter.format(result.resultingBalance(), this.economyConfig));
        return true;
    }

    private MoneyAmount parsePositiveAmount(final CommandSender sender, final String rawAmount) {
        final MoneyAmount amount;
        try {
            amount = MoneyAmount.fromMajor(new BigDecimal(rawAmount.trim()), this.economyConfig.fractionalDigits());
        } catch (final RuntimeException exception) {
            sender.sendMessage("Invalid amount: " + rawAmount);
            return null;
        }

        if (!amount.isPositive()) {
            sender.sendMessage("Amount must be positive.");
            return null;
        }
        return amount;
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

    private void sendUsage(final CommandSender sender, final String label) {
        sender.sendMessage("Usage:");
        sender.sendMessage("/" + label + " balance <player>");
        sender.sendMessage("/" + label + " give <player> <amount>");
        sender.sendMessage("/" + label + " take <player> <amount>");
        sender.sendMessage("/" + label + " set <player> <amount>");
        sender.sendMessage("/" + label + " reset <player>");
    }
}
