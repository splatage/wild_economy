package com.splatage.wild_economy.command;

public final class ShopSellAllSubcommand {
}
package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.domain.SellAllResult;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopSellAllSubcommand {

    private final ExchangeService exchangeService;

    public ShopSellAllSubcommand(final ExchangeService exchangeService) {
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        final SellAllResult result = this.exchangeService.sellAll(player.getUniqueId());
        player.sendMessage(result.message());
        if (!result.skippedDescriptions().isEmpty()) {
            player.sendMessage("Skipped: " + String.join(", ", result.skippedDescriptions()));
        }
        return true;
    }
}
