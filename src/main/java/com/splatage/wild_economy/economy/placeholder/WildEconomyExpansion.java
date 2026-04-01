package com.splatage.wild_economy.economy.placeholder;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.EconomyFormatter;
import com.splatage.wild_economy.economy.model.BalanceRankEntry;
import com.splatage.wild_economy.economy.service.BaltopService;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.exchange.supplier.SupplierScope;
import com.splatage.wild_economy.exchange.supplier.SupplierStatsService;
import com.splatage.wild_economy.exchange.supplier.TopSupplierEntry;
import com.splatage.wild_economy.title.service.ResolvedTitleService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WildEconomyExpansion extends PlaceholderExpansion {

    private static final int BALTOP_PAGE_SIZE = 10;

    private final WildEconomyPlugin plugin;
    private final EconomyService economyService;
    private final BaltopService baltopService;
    private final SupplierStatsService supplierStatsService;
    private final EconomyConfig economyConfig;
    private final ResolvedTitleService resolvedTitleService;

    public WildEconomyExpansion(
        final WildEconomyPlugin plugin,
        final EconomyService economyService,
        final BaltopService baltopService,
        final SupplierStatsService supplierStatsService,
        final EconomyConfig economyConfig,
        final ResolvedTitleService resolvedTitleService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.baltopService = Objects.requireNonNull(baltopService, "baltopService");
        this.supplierStatsService = Objects.requireNonNull(supplierStatsService, "supplierStatsService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
        this.resolvedTitleService = Objects.requireNonNull(resolvedTitleService, "resolvedTitleService");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "wildeco";
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
            case "baltop_rank" -> this.resolveBaltopRank(player);
            case "supplier_weekly_total" -> this.resolveSupplierTotal(player, SupplierScope.WEEKLY);
            case "supplier_alltime_total", "supplier_all_time_total" -> this.resolveSupplierTotal(player, SupplierScope.ALL_TIME);
            case "supplier_weekly_rank" -> this.resolveSupplierRank(player, SupplierScope.WEEKLY);
            case "supplier_alltime_rank", "supplier_all_time_rank" -> this.resolveSupplierRank(player, SupplierScope.ALL_TIME);
            case "top_supplier_weekly_name" -> this.resolveTopSupplierName(SupplierScope.WEEKLY);
            case "top_supplier_weekly_total" -> this.resolveTopSupplierTotal(SupplierScope.WEEKLY);
            case "top_supplier_alltime_name", "top_supplier_all_time_name" -> this.resolveTopSupplierName(SupplierScope.ALL_TIME);
            case "top_supplier_alltime_total", "top_supplier_all_time_total" -> this.resolveTopSupplierTotal(SupplierScope.ALL_TIME);
            case "title" -> this.resolveActiveTitle(player);
            case "title_key" -> this.resolveResolvedTitleField(player, Field.KEY);
            case "title_source" -> this.resolveResolvedTitleField(player, Field.SOURCE);
            case "title_family" -> this.resolveResolvedTitleField(player, Field.FAMILY);
            default -> this.resolveBaltopPlaceholder(normalized);
        };
    }

    private String resolveActiveTitle(final OfflinePlayer player) {
        return this.resolvedTitleService.getResolvedTitle(player)
                .map(resolved -> resolved.text())
                .orElse("");
    }

    private String resolveResolvedTitleField(final OfflinePlayer player, final Field field) {
        return this.resolvedTitleService.getResolvedTitle(player)
                .map(resolved -> switch (field) {
                    case KEY -> resolved.key();
                    case SOURCE -> resolved.source().name().toLowerCase(Locale.ROOT);
                    case FAMILY -> resolved.family();
                })
                .orElse("");
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

    private String resolveBaltopRank(final OfflinePlayer player) {
        if (player == null) {
            return "";
        }
        final int rank = this.baltopService.getRank(player.getUniqueId());
        return rank <= 0 ? "" : Integer.toString(rank);
    }

    private String resolveSupplierTotal(final OfflinePlayer player, final SupplierScope scope) {
        if (player == null) {
            return "";
        }
        return Long.toString(this.supplierStatsService.getPlayerTotalSupplied(scope, player.getUniqueId()));
    }

    private String resolveSupplierRank(final OfflinePlayer player, final SupplierScope scope) {
        if (player == null) {
            return "";
        }
        final int rank = this.supplierStatsService.getPlayerRank(scope, player.getUniqueId());
        return rank <= 0 ? "" : Integer.toString(rank);
    }

    private String resolveTopSupplierName(final SupplierScope scope) {
        final Optional<TopSupplierEntry> topSupplier = this.supplierStatsService.getTopSupplier(scope);
        if (topSupplier.isEmpty()) {
            return "";
        }
        final String displayName = topSupplier.get().displayName();
        return displayName == null || displayName.isBlank()
                ? topSupplier.get().playerId().toString()
                : displayName;
    }

    private String resolveTopSupplierTotal(final SupplierScope scope) {
        return this.supplierStatsService.getTopSupplier(scope)
                .map(TopSupplierEntry::totalQuantitySold)
                .map(String::valueOf)
                .orElse("");
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

    private enum Field {
        KEY,
        SOURCE,
        FAMILY
    }
}
