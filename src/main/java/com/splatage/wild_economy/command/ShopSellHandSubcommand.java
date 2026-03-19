package com.splatage.wild_economy.command;

public final class ShopSellHandSubcommand {
}
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellHandSubcommand {

    private final ExchangeService exchangeService;

    public ShopSellHandSubcommand(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        final SellHandResult result = this.exchangeService.sellHand(player.getUniqueId());
        player.sendMessage(result.message());
        return true;
    }
}
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellHandResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellHandSubcommand {

    private final ExchangeService exchangeService;

    public ShopSellHandSubcommand(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        final SellHandResult result = this.exchangeService.sellHand(player.getUniqueId());
        player.sendMessage(result.message());
        return true;
    }
}
