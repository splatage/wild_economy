package com.splatage.wild_economy.command;

import com.splatage.wild_economy.gui.ShopMenuRouter;
import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopOpenSubcommand {

    private final ShopMenuRouter shopMenuRouter;

    public ShopOpenSubcommand(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = Objects.requireNonNull(shopMenuRouter, "shopMenuRouter");
    }

    public boolean execute(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        this.shopMenuRouter.openRoot(player);
        return true;
    }
}
