package com.splatage.wild_economy.economy.placeholder;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.EconomyFormatter;
import com.splatage.wild_economy.economy.model.BalanceRankEntry;
import com.splatage.wild_economy.economy.service.BaltopService;
import com.splatage.wild_economy.economy.service.EconomyService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WildEconomyExpansion extends PlaceholderExpansion {

    private static final int BALTOP_PAGE_SIZE = 10;

    private final WildEconomyPlugin plugin;
    private final EconomyService economyService;
    private final BaltopService baltopService;
    private final EconomyConfig economyConfig;

    public WildEconomyExpansion(
        final WildEconomyPlugin plugin,
        final EconomyService economyService,
        final BaltopService baltopService,
        final EconomyConfig economyConfig
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.baltopService = Objects.requireNonNull(baltopService, "baltopService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "wild_economy";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", this.plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(final OfflinePlayer player, final @NotNull String params) {
        final String normalized = params.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "balance" -> this.resolveBalance(player);
            case "balance_formatted" -> this.resolveFormattedBalance(player);
            default -> this.resolveBaltopPlaceholder(normalized);
        };
    }

    private String resolveBalance(final OfflinePlayer player) {
        if (player == null) {
            return "";
        }
        return this.economyService.getBalance(player.getUniqueId())
                .toMajor(this.economyConfig.fractionalDigits())
                .toPlainString();
    }

    private String resolveFormattedBalance(final OfflinePlayer player) {
        if (player == null) {
            return "";
        }
        return EconomyFormatter.format(
                this.economyService.getBalance(player.getUniqueId()),
                this.economyConfig
        );
    }

    private String resolveBaltopPlaceholder(final String params) {
        if (params.startsWith("baltop_name_")) {
            final Integer rank = this.parsePositiveIntSuffix(params, "baltop_name_");
            if (rank == null) {
                return null;
            }
            final BalanceRankEntry entry = this.findBaltopEntry(rank);
            if (entry == null) {
                return "";
            }
            return entry.displayName() == null || entry.displayName().isBlank()
                    ? entry.playerId().toString()
                    : entry.displayName();
        }

        if (params.startsWith("baltop_balance_")) {
            final Integer rank = this.parsePositiveIntSuffix(params, "baltop_balance_");
            if (rank == null) {
                return null;
            }
            final BalanceRankEntry entry = this.findBaltopEntry(rank);
            if (entry == null) {
                return "";
            }
            return EconomyFormatter.format(entry.balance(), this.economyConfig);
        }

        return null;
    }

    private Integer parsePositiveIntSuffix(final String value, final String prefix) {
        final String suffix = value.substring(prefix.length()).trim();
        if (suffix.isEmpty()) {
            return null;
        }
        try {
            final int parsed = Integer.parseInt(suffix);
            return parsed > 0 ? parsed : null;
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    private BalanceRankEntry findBaltopEntry(final int rank) {
        final int page = ((rank - 1) / BALTOP_PAGE_SIZE) + 1;
        final int indexInPage = (rank - 1) % BALTOP_PAGE_SIZE;

        final List<BalanceRankEntry> entries = this.baltopService.getPage(page, BALTOP_PAGE_SIZE);
        if (indexInPage < 0 || indexInPage >= entries.size()) {
            return null;
        }
        return entries.get(indexInPage);
    }
}
