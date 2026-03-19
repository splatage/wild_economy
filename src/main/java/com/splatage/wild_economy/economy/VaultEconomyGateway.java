package com.splatage.wild_economy.economy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class VaultEconomyGateway implements EconomyGateway {

    private final Economy economy;

    public VaultEconomyGateway(final Economy economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    @Override
    public EconomyResult deposit(final UUID playerId, final BigDecimal amount) {
        final OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        final EconomyResponse response = this.economy.depositPlayer(player, amount.doubleValue());
        return new EconomyResult(response.transactionSuccess(), response.errorMessage == null ? "" : response.errorMessage);
    }

    @Override
    public EconomyResult withdraw(final UUID playerId, final BigDecimal amount) {
        final OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        final EconomyResponse response = this.economy.withdrawPlayer(player, amount.doubleValue());
        return new EconomyResult(response.transactionSuccess(), response.errorMessage == null ? "" : response.errorMessage);
    }

    @Override
    public BigDecimal getBalance(final UUID playerId) {
        final OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return BigDecimal.valueOf(this.economy.getBalance(player));
    }
}
