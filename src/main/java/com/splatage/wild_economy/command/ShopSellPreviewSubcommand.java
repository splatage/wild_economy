package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellPreviewResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.ChatColor;
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
                ? this.exchangeService.previewContainerSell(player)
                : this.exchangeService.previewInventorySell(player);
            ExchangeMessageFormatter.sendSellPreview(player, result);
        });

        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender, args);
    }
}
