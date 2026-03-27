package com.splatage.wild_economy.economy.vault;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.EconomyFormatter;
import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.economy.service.EconomyService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class WildEconomyVaultProvider extends AbstractEconomy {

    private final WildEconomyPlugin plugin;
    private final EconomyService economyService;
    private final EconomyConfig economyConfig;

    public WildEconomyVaultProvider(
        final WildEconomyPlugin plugin,
        final EconomyService economyService,
        final EconomyConfig economyConfig
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    @Override
    public boolean isEnabled() {
        return this.plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "wild_economy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return this.economyConfig.fractionalDigits();
    }

    @Override
    public String format(final double amount) {
        return EconomyFormatter.format(this.toMoneyAmount(amount), this.economyConfig);
    }

    @Override
    public String currencyNamePlural() {
        return this.economyConfig.currencyPlural();
    }

    @Override
    public String currencyNameSingular() {
        return this.economyConfig.currencySingular();
    }

    @Override
    public boolean hasAccount(final String playerName) {
        return this.resolveKnownOfflinePlayer(playerName) != null;
    }

    @Override
    public boolean hasAccount(final OfflinePlayer player) {
        return this.isKnownPlayer(player);
    }

    @Override
    public boolean hasAccount(final String playerName, final String worldName) {
        return this.hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(final OfflinePlayer player, final String worldName) {
        return this.hasAccount(player);
    }

    @Override
    public double getBalance(final String playerName) {
        final OfflinePlayer player = this.resolveKnownOfflinePlayer(playerName);
        return player == null ? 0.0D : this.getBalance(player);
    }

    @Override
    public double getBalance(final OfflinePlayer player) {
        if (!this.isKnownPlayer(player)) {
            return 0.0D;
        }
        return this.economyService
                .getBalance(player.getUniqueId())
                .toMajor(this.economyConfig.fractionalDigits())
                .doubleValue();
    }

    @Override
    public double getBalance(final String playerName, final String worldName) {
        return this.getBalance(playerName);
    }

    @Override
    public double getBalance(final OfflinePlayer player, final String worldName) {
        return this.getBalance(player);
    }

    @Override
    public boolean has(final String playerName, final double amount) {
        final OfflinePlayer player = this.resolveKnownOfflinePlayer(playerName);
        return player != null && this.has(player, amount);
    }

    @Override
    public boolean has(final OfflinePlayer player, final double amount) {
        if (!this.isKnownPlayer(player)) {
            return false;
        }
        return this.economyService
                .getBalanceForSensitiveOperation(player.getUniqueId())
                .minorUnits() >= this.toMoneyAmount(amount).minorUnits();
    }

    @Override
    public boolean has(final String playerName, final String worldName, final double amount) {
        return this.has(playerName, amount);
    }

    @Override
    public boolean has(final OfflinePlayer player, final String worldName, final double amount) {
        return this.has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(final String playerName, final double amount) {
        final OfflinePlayer player = this.resolveKnownOfflinePlayer(playerName);
        if (player == null) {
            return this.failure(amount, 0.0D, "Unknown player");
        }
        return this.withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(final OfflinePlayer player, final double amount) {
        if (!this.isKnownPlayer(player)) {
            return this.failure(amount, 0.0D, "Unknown player");
        }

        final MoneyAmount moneyAmount = this.toMoneyAmount(amount);
        final EconomyMutationResult result = this.economyService.withdraw(
                player.getUniqueId(),
                moneyAmount,
                EconomyReason.VAULT_WITHDRAW,
                "vault",
                null
        );

        final double balance = result.resultingBalance()
                .toMajor(this.economyConfig.fractionalDigits())
                .doubleValue();
        return result.success()
                ? this.success(amount, balance)
                : this.failure(amount, balance, result.message());
    }

    @Override
    public EconomyResponse withdrawPlayer(final String playerName, final String worldName, final double amount) {
        return this.withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(final OfflinePlayer player, final String worldName, final double amount) {
        return this.withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(final String playerName, final double amount) {
        final OfflinePlayer player = this.resolveKnownOfflinePlayer(playerName);
        if (player == null) {
            return this.failure(amount, 0.0D, "Unknown player");
        }
        return this.depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(final OfflinePlayer player, final double amount) {
        if (!this.isKnownPlayer(player)) {
            return this.failure(amount, 0.0D, "Unknown player");
        }

        final MoneyAmount moneyAmount = this.toMoneyAmount(amount);
        final EconomyMutationResult result = this.economyService.deposit(
                player.getUniqueId(),
                moneyAmount,
                EconomyReason.VAULT_DEPOSIT,
                "vault",
                null
        );

        final double balance = result.resultingBalance()
                .toMajor(this.economyConfig.fractionalDigits())
                .doubleValue();
        return result.success()
                ? this.success(amount, balance)
                : this.failure(amount, balance, result.message());
    }

    @Override
    public EconomyResponse depositPlayer(final String playerName, final String worldName, final double amount) {
        return this.depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(final OfflinePlayer player, final String worldName, final double amount) {
        return this.depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(final String name, final String player) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse createBank(final String name, final OfflinePlayer player) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(final String name) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(final String name) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse bankHas(final String name, final double amount) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(final String name, final double amount) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(final String name, final double amount) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(final String name, final String playerName) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(final String name, final OfflinePlayer player) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(final String name, final String playerName) {
        return this.notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(final String name, final OfflinePlayer player) {
        return this.notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(final String playerName) {
        return this.hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(final OfflinePlayer player) {
        return this.hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(final String playerName, final String worldName) {
        return this.createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(final OfflinePlayer player, final String worldName) {
        return this.createPlayerAccount(player);
    }

    private OfflinePlayer resolveKnownOfflinePlayer(final String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        final OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return this.isKnownPlayer(player) ? player : null;
    }

    private boolean isKnownPlayer(final OfflinePlayer player) {
        return player != null && (player.isOnline() || player.hasPlayedBefore());
    }

    private MoneyAmount toMoneyAmount(final double amount) {
        if (amount <= 0.0D) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return MoneyAmount.fromMajor(BigDecimal.valueOf(amount), this.economyConfig.fractionalDigits());
    }

    private EconomyResponse success(final double amount, final double balance) {
        return new EconomyResponse(
                amount,
                balance,
                EconomyResponse.ResponseType.SUCCESS,
                ""
        );
    }

    private EconomyResponse failure(final double amount, final double balance, final String message) {
        return new EconomyResponse(
                amount,
                balance,
                EconomyResponse.ResponseType.FAILURE,
                message == null ? "" : message
        );
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(
                0.0D,
                0.0D,
                EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                "Bank support is not implemented"
        );
    }
}
