package com.splatage.wild_economy.store.action;

import com.splatage.wild_economy.store.model.StoreAction;
import com.splatage.wild_economy.store.model.StoreActionType;
import com.splatage.wild_economy.store.model.StoreProduct;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public final class SimpleProductActionExecutor implements ProductActionExecutor {

    @Override
    public StoreActionExecutionResult execute(final Player player, final StoreProduct product) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(product, "product");

        for (final StoreAction action : product.actions()) {
            final StoreActionExecutionResult result = this.executeAction(player, product, action);
            if (!result.success()) {
                return result;
            }
        }
        return StoreActionExecutionResult.succeed();
    }

    private StoreActionExecutionResult executeAction(
        final Player player,
        final StoreProduct product,
        final StoreAction action
    ) {
        return switch (action.type()) {
            case CONSOLE_COMMAND -> this.executeConsoleCommand(player, product, action.value());
            case MESSAGE -> {
                player.sendMessage(this.render(player, product, action.value()));
                yield StoreActionExecutionResult.succeed();
            }
        };
    }

    private StoreActionExecutionResult executeConsoleCommand(
        final Player player,
        final StoreProduct product,
        final String commandTemplate
    ) {
        final String command = this.render(player, product, commandTemplate);
        final ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        final boolean success = Bukkit.dispatchCommand(console, command);
        if (!success) {
            return StoreActionExecutionResult.failure("Action failed while dispatching command: " + command);
        }
        return StoreActionExecutionResult.succeed();
    }

    private String render(final Player player, final StoreProduct product, final String template) {
        return template
                .replace("{player}", player.getName())
                .replace("{player_uuid}", player.getUniqueId().toString())
                .replace("{product_id}", product.productId());
    }
}
