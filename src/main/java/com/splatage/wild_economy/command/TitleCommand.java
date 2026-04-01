package com.splatage.wild_economy.command;

import com.splatage.wild_economy.gui.TitleMenu;
import com.splatage.wild_economy.title.service.ResolvedTitleService;
import com.splatage.wild_economy.title.service.TitleSelectionService;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TitleCommand implements CommandExecutor {

    private final TitleMenu titleMenu;
    private final TitleSelectionService titleSelectionService;
    private final ResolvedTitleService resolvedTitleService;

    public TitleCommand(
            final TitleMenu titleMenu,
            final TitleSelectionService titleSelectionService,
            final ResolvedTitleService resolvedTitleService
    ) {
        this.titleMenu = Objects.requireNonNull(titleMenu, "titleMenu");
        this.titleSelectionService = Objects.requireNonNull(titleSelectionService, "titleSelectionService");
        this.resolvedTitleService = Objects.requireNonNull(resolvedTitleService, "resolvedTitleService");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            this.titleMenu.open(player, 0);
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "clear", "off", "none", "auto", "reset" -> this.clearSelection(player);
            default -> {
                player.sendMessage("Usage: /" + label + " [clear]");
                yield true;
            }
        };
    }

    private boolean clearSelection(final Player player) {
        this.titleSelectionService.clearSelectedTitleKey(player);
        this.resolvedTitleService.invalidate(player.getUniqueId());
        this.resolvedTitleService.warm(player);
        player.sendMessage("Your active title has been cleared. Automatic title selection is now in effect.");
        return true;
    }
}
