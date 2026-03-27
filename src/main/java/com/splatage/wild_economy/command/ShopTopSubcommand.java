package com.splatage.wild_economy.command;

import com.splatage.wild_economy.exchange.supplier.SupplierContributionEntry;
import com.splatage.wild_economy.exchange.supplier.SupplierPlayerDetail;
import com.splatage.wild_economy.exchange.supplier.SupplierScope;
import com.splatage.wild_economy.exchange.supplier.SupplierStatsService;
import com.splatage.wild_economy.exchange.supplier.TopSupplierEntry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopTopSubcommand implements CommandExecutor {

    private static final int TOP_LIMIT = 10;
    private static final int DETAIL_ITEM_LIMIT = 8;

    private final SupplierStatsService supplierStatsService;

    public ShopTopSubcommand(final SupplierStatsService supplierStatsService) {
        this.supplierStatsService = Objects.requireNonNull(supplierStatsService, "supplierStatsService");
    }

    public boolean execute(final CommandSender sender, final String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("player")) {
            return this.executePlayerDetail(sender, args);
        }

        final SupplierScope scope = args.length >= 1 ? SupplierScope.fromToken(args[0]) : SupplierScope.WEEKLY;
        this.sendLeaderboard(sender, scope);
        return true;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        return this.execute(sender, args);
    }

    private boolean executePlayerDetail(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /shop top player <player> [weekly|alltime]", NamedTextColor.RED));
            return true;
        }

        final Optional<UUID> resolvedPlayerId = this.resolvePlayerId(args[1]);
        if (resolvedPlayerId.isEmpty()) {
            sender.sendMessage(Component.text("No supplier data found for '" + args[1] + "'.", NamedTextColor.RED));
            return true;
        }

        final SupplierScope scope = args.length >= 3 ? SupplierScope.fromToken(args[2]) : SupplierScope.WEEKLY;
        final Optional<SupplierPlayerDetail> detail = this.supplierStatsService.getPlayerDetail(scope, resolvedPlayerId.get(), DETAIL_ITEM_LIMIT);
        if (detail.isEmpty()) {
            sender.sendMessage(Component.text("No supplier data found for that player in this scope.", NamedTextColor.RED));
            return true;
        }

        this.sendPlayerDetail(sender, detail.get());
        return true;
    }

    private Optional<UUID> resolvePlayerId(final String token) {
        try {
            return Optional.of(UUID.fromString(token));
        } catch (final IllegalArgumentException ignored) {
            return this.supplierStatsService.findPlayerIdByName(token);
        }
    }

    private void sendLeaderboard(final CommandSender sender, final SupplierScope scope) {
        final List<TopSupplierEntry> entries = this.supplierStatsService.getTopSuppliers(scope, TOP_LIMIT);
        sender.sendMessage(this.scopeHeader(scope, null, null));
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("No supplier data found for this scope yet.", NamedTextColor.RED));
            return;
        }

        for (final TopSupplierEntry entry : entries) {
            final Component playerName = Component.text(entry.displayName(), NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/shop top player " + entry.playerId() + " " + scope.commandToken()))
                .hoverEvent(HoverEvent.showText(Component.text("View contribution breakdown", NamedTextColor.GRAY)));
            final Component line = Component.text()
                .append(Component.text("#" + entry.rank() + " ", NamedTextColor.GOLD))
                .append(playerName)
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(formatQuantity(entry.totalQuantitySold()) + " items", NamedTextColor.GREEN))
                .build();
            sender.sendMessage(line);
        }
    }

    private void sendPlayerDetail(final CommandSender sender, final SupplierPlayerDetail detail) {
        sender.sendMessage(this.scopeHeader(detail.scope(), detail.displayName(), detail.playerId()));
        sender.sendMessage(
            Component.text()
                .append(Component.text("Total supplied: ", NamedTextColor.GOLD))
                .append(Component.text(formatQuantity(detail.totalQuantitySold()) + " items", NamedTextColor.GREEN))
                .build()
        );
        sender.sendMessage(
            Component.text()
                .append(Component.text("Rank: ", NamedTextColor.GOLD))
                .append(Component.text("#" + detail.rank(), NamedTextColor.AQUA))
                .build()
        );
        if (detail.topContributions().isEmpty()) {
            sender.sendMessage(Component.text("No item contribution details found for this scope.", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("Top contributions:", NamedTextColor.GOLD));
            for (final SupplierContributionEntry contribution : detail.topContributions()) {
                sender.sendMessage(
                    Component.text()
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(contribution.displayName(), NamedTextColor.AQUA))
                        .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(formatQuantity(contribution.quantitySold()), NamedTextColor.GREEN))
                        .build()
                );
            }
        }
        sender.sendMessage(
            Component.text("[Back to Top]", NamedTextColor.YELLOW, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/shop top " + detail.scope().commandToken()))
                .hoverEvent(HoverEvent.showText(Component.text("Return to the leaderboard", NamedTextColor.GRAY)))
        );
    }

    private Component scopeHeader(final SupplierScope scope, final String playerName, final UUID playerId) {
        final Component title = playerName == null
            ? Component.text("Shop Top", NamedTextColor.GOLD, TextDecoration.BOLD)
            : Component.text("Shop Contributions: " + playerName, NamedTextColor.GOLD, TextDecoration.BOLD);
        return Component.text()
            .append(title)
            .append(Component.text(" ", NamedTextColor.WHITE))
            .append(this.scopeButton("Weekly", SupplierScope.WEEKLY, scope, playerId))
            .append(Component.text(" ", NamedTextColor.DARK_GRAY))
            .append(this.scopeButton("All Time", SupplierScope.ALL_TIME, scope, playerId))
            .build();
    }

    private Component scopeButton(final String label, final SupplierScope buttonScope, final SupplierScope activeScope, final UUID playerId) {
        final boolean active = buttonScope == activeScope;
        final NamedTextColor color = active ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        final Component button = active
            ? Component.text("[" + label + "]", color, TextDecoration.BOLD)
            : Component.text("[" + label + "]", color);
        final String command = playerId == null
            ? "/shop top " + buttonScope.commandToken()
            : "/shop top player " + playerId + " " + buttonScope.commandToken();
        return button
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text("View " + label.toLowerCase() + (playerId == null ? " supplier rankings" : " contribution view"), NamedTextColor.GRAY)));
    }

    private static String formatQuantity(final long value) {
        return String.format(java.util.Locale.US, "%,d", value);
    }
}
