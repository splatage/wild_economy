package com.splatage.wild_economy.economy.service;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.model.EconomyAccountRecord;
import com.splatage.wild_economy.economy.model.EconomyLedgerEntry;
import com.splatage.wild_economy.economy.model.EconomyMutationResult;
import com.splatage.wild_economy.economy.model.EconomyReason;
import com.splatage.wild_economy.economy.model.EconomyTransferResult;
import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.economy.repository.EconomyAccountRepository;
import com.splatage.wild_economy.economy.repository.EconomyLedgerRepository;
import com.splatage.wild_economy.economy.repository.EconomyNameCacheRepository;
import com.splatage.wild_economy.persistence.TransactionRunner;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class EconomyServiceImpl implements EconomyService {

    private static final int MAX_BALANCE_UPDATE_ATTEMPTS = 3;

    private final EconomyConfig economyConfig;
    private final EconomyAccountRepository economyAccountRepository;
    private final EconomyLedgerRepository economyLedgerRepository;
    private final EconomyNameCacheRepository economyNameCacheRepository;
    private final TransactionRunner transactionRunner;
    private final BalanceCache balanceCache;
    private final BaltopService baltopService;
    private final ConcurrentMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    public EconomyServiceImpl(
        final EconomyConfig economyConfig,
        final EconomyAccountRepository economyAccountRepository,
        final EconomyLedgerRepository economyLedgerRepository,
        final EconomyNameCacheRepository economyNameCacheRepository,
        final TransactionRunner transactionRunner,
        final BalanceCache balanceCache,
        final BaltopService baltopService
    ) {
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
        this.economyAccountRepository = Objects.requireNonNull(economyAccountRepository, "economyAccountRepository");
        this.economyLedgerRepository = Objects.requireNonNull(economyLedgerRepository, "economyLedgerRepository");
        this.economyNameCacheRepository = Objects.requireNonNull(economyNameCacheRepository, "economyNameCacheRepository");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner");
        this.balanceCache = Objects.requireNonNull(balanceCache, "balanceCache");
        this.baltopService = Objects.requireNonNull(baltopService, "baltopService");
    }

    @Override
    public void warmPlayerSession(final UUID playerId, final String playerName) {
        final Object lock = this.lockFor(playerId);
        synchronized (lock) {
            final long now = this.now();
            EconomyAccountRecord account = this.economyAccountRepository.findByPlayerId(playerId);

            if (account == null && this.economyConfig.autoCreateOnJoin()) {
                account = this.transactionRunner.run(connection -> {
                    final EconomyAccountRecord created = this.economyAccountRepository.createIfAbsent(connection, playerId, now);
                    if (this.hasPlayerName(playerName)) {
                        this.economyNameCacheRepository.upsert(connection, playerId, playerName, now);
                    }
                    return created;
                });
            } else if (this.hasPlayerName(playerName)) {
                this.transactionRunner.run(connection -> {
                    this.economyNameCacheRepository.upsert(connection, playerId, playerName, now);
                    return null;
                });
            }

            final MoneyAmount balance = account == null ? MoneyAmount.zero() : account.balance();
            this.balanceCache.put(playerId, balance, now, true);
        }
    }

    @Override
    public void flushPlayerSession(final UUID playerId, final String playerName) {
        final Object lock = this.lockFor(playerId);
        synchronized (lock) {
            final long now = this.now();
            if (this.hasPlayerName(playerName)) {
                this.transactionRunner.run(connection -> {
                    this.economyNameCacheRepository.upsert(connection, playerId, playerName, now);
                    return null;
                });
            }
            this.balanceCache.evict(playerId);
        }
    }

    @Override
    public MoneyAmount getBalance(final UUID playerId) {
        return this.loadBalance(playerId, false);
    }

    @Override
    public MoneyAmount getBalanceForSensitiveOperation(final UUID playerId) {
        return this.loadBalance(playerId, this.economyConfig.refreshBeforeSensitiveOperations());
    }

    @Override
    public EconomyMutationResult deposit(
        final UUID playerId,
        final MoneyAmount amount,
        final EconomyReason reason,
        final String referenceType,
        final String referenceId
    ) {
        if (amount == null || !amount.isPositive()) {
            return EconomyMutationResult.failure("Deposit amount must be positive", this.getBalance(playerId));
        }

        final Object lock = this.lockFor(playerId);
        synchronized (lock) {
            for (int attempt = 0; attempt < MAX_BALANCE_UPDATE_ATTEMPTS; attempt++) {
                final long now = this.now();
                try {
                    final EconomyMutationResult result = this.transactionRunner.run(connection -> {
                        this.economyAccountRepository.createIfAbsent(connection, playerId, now);
                        final MoneyAmount currentBalance = this.balanceOf(this.economyAccountRepository.findByPlayerId(connection, playerId));
                        final MoneyAmount newBalance = currentBalance.add(amount);
                        if (!this.economyAccountRepository.updateIfBalanceMatches(connection, playerId, currentBalance, newBalance, now)) {
                            throw new ConcurrentBalanceUpdateException();
                        }
                        this.economyLedgerRepository.insert(connection, new EconomyLedgerEntry(
                                playerId,
                                reason,
                                amount,
                                newBalance,
                                null,
                                referenceType,
                                referenceId,
                                now
                        ));
                        return EconomyMutationResult.success(newBalance);
                    });

                    this.balanceCache.put(playerId, result.resultingBalance(), now, this.balanceCache.isOnline(playerId));
                    this.baltopService.invalidate();
                    return result;
                } catch (final ConcurrentBalanceUpdateException ignored) {
                    // Retry against the latest shared-database state.
                }
            }

            return EconomyMutationResult.failure(
                    "Balance changed during update, please try again.",
                    this.getBalanceForSensitiveOperation(playerId)
            );
        }
    }

    @Override
    public EconomyMutationResult withdraw(
        final UUID playerId,
        final MoneyAmount amount,
        final EconomyReason reason,
        final String referenceType,
        final String referenceId
    ) {
        if (amount == null || !amount.isPositive()) {
            return EconomyMutationResult.failure("Withdraw amount must be positive", this.getBalance(playerId));
        }

        final BalanceCache.CachedBalance cachedBalance = this.balanceCache.get(playerId).orElse(null);
        if (cachedBalance != null && cachedBalance.balance().minorUnits() < amount.minorUnits()) {
            return EconomyMutationResult.failure("Insufficient funds", cachedBalance.balance());
        }

        final Object lock = this.lockFor(playerId);
        synchronized (lock) {
            for (int attempt = 0; attempt < MAX_BALANCE_UPDATE_ATTEMPTS; attempt++) {
                final long now = this.now();
                try {
                    final EconomyMutationResult result = this.transactionRunner.run(connection -> {
                        this.economyAccountRepository.createIfAbsent(connection, playerId, now);
                        final MoneyAmount currentBalance = this.balanceOf(this.economyAccountRepository.findByPlayerId(connection, playerId));
                        if (currentBalance.minorUnits() < amount.minorUnits()) {
                            return EconomyMutationResult.failure("Insufficient funds", currentBalance);
                        }

                        final MoneyAmount newBalance = currentBalance.subtract(amount);
                        if (!this.economyAccountRepository.updateIfBalanceMatches(connection, playerId, currentBalance, newBalance, now)) {
                            throw new ConcurrentBalanceUpdateException();
                        }

                        this.economyLedgerRepository.insert(connection, new EconomyLedgerEntry(
                                playerId,
                                reason,
                                MoneyAmount.ofMinor(-amount.minorUnits()),
                                newBalance,
                                null,
                                referenceType,
                                referenceId,
                                now
                        ));
                        return EconomyMutationResult.success(newBalance);
                    });

                    if (!result.success()) {
                        return result;
                    }

                    this.balanceCache.put(playerId, result.resultingBalance(), now, this.balanceCache.isOnline(playerId));
                    this.baltopService.invalidate();
                    return result;
                } catch (final ConcurrentBalanceUpdateException ignored) {
                    // Retry against the latest shared-database state.
                }
            }

            return EconomyMutationResult.failure(
                    "Balance changed during update, please try again.",
                    this.getBalanceForSensitiveOperation(playerId)
            );
        }
    }

    @Override
    public EconomyMutationResult setBalance(
        final UUID playerId,
        final MoneyAmount balance,
        final EconomyReason reason,
        final String referenceType,
        final String referenceId
    ) {
        if (balance == null || balance.isNegative()) {
            return EconomyMutationResult.failure("Balance cannot be negative", this.getBalance(playerId));
        }

        final Object lock = this.lockFor(playerId);
        synchronized (lock) {
            for (int attempt = 0; attempt < MAX_BALANCE_UPDATE_ATTEMPTS; attempt++) {
                final long now = this.now();
                try {
                    final EconomyMutationResult result = this.transactionRunner.run(connection -> {
                        this.economyAccountRepository.createIfAbsent(connection, playerId, now);
                        final MoneyAmount currentBalance = this.balanceOf(this.economyAccountRepository.findByPlayerId(connection, playerId));
                        final MoneyAmount delta = balance.subtract(currentBalance);
                        if (!this.economyAccountRepository.updateIfBalanceMatches(connection, playerId, currentBalance, balance, now)) {
                            throw new ConcurrentBalanceUpdateException();
                        }
                        this.economyLedgerRepository.insert(connection, new EconomyLedgerEntry(
                                playerId,
                                reason,
                                delta,
                                balance,
                                null,
                                referenceType,
                                referenceId,
                                now
                        ));
                        return EconomyMutationResult.success(balance);
                    });

                    this.balanceCache.put(playerId, result.resultingBalance(), now, this.balanceCache.isOnline(playerId));
                    this.baltopService.invalidate();
                    return result;
                } catch (final ConcurrentBalanceUpdateException ignored) {
                    // Retry against the latest shared-database state.
                }
            }

            return EconomyMutationResult.failure(
                    "Balance changed during update, please try again.",
                    this.getBalanceForSensitiveOperation(playerId)
            );
        }
    }

    @Override
    public EconomyTransferResult transfer(
        final UUID senderId,
        final UUID recipientId,
        final MoneyAmount amount,
        final String referenceType,
        final String referenceId
    ) {
        if (senderId.equals(recipientId)) {
            final MoneyAmount senderBalance = this.getBalance(senderId);
            return EconomyTransferResult.failure("Sender and recipient must be different", senderBalance, senderBalance);
        }
        if (amount == null || !amount.isPositive()) {
            return EconomyTransferResult.failure(
                    "Transfer amount must be positive",
                    this.getBalance(senderId),
                    this.getBalance(recipientId)
            );
        }

        final BalanceCache.CachedBalance cachedSenderBalance = this.balanceCache.get(senderId).orElse(null);
        if (cachedSenderBalance != null && cachedSenderBalance.balance().minorUnits() < amount.minorUnits()) {
            final MoneyAmount recipientBalance = this.balanceCache.get(recipientId)
                    .map(BalanceCache.CachedBalance::balance)
                    .orElseGet(() -> this.getBalance(recipientId));
            return EconomyTransferResult.failure("Insufficient funds", cachedSenderBalance.balance(), recipientBalance);
        }

        final UUID first = this.order(senderId, recipientId) <= 0 ? senderId : recipientId;
        final UUID second = first.equals(senderId) ? recipientId : senderId;

        final Object firstLock = this.lockFor(first);
        final Object secondLock = this.lockFor(second);

        synchronized (firstLock) {
            synchronized (secondLock) {
                for (int attempt = 0; attempt < MAX_BALANCE_UPDATE_ATTEMPTS; attempt++) {
                    final long now = this.now();
                    try {
                        final EconomyTransferResult result = this.transactionRunner.run(connection -> {
                            this.economyAccountRepository.createIfAbsent(connection, senderId, now);
                            this.economyAccountRepository.createIfAbsent(connection, recipientId, now);

                            final MoneyAmount senderBalance = this.balanceOf(this.economyAccountRepository.findByPlayerId(connection, senderId));
                            if (senderBalance.minorUnits() < amount.minorUnits()) {
                                final MoneyAmount recipientBalance = this.balanceOf(this.economyAccountRepository.findByPlayerId(connection, recipientId));
                                return EconomyTransferResult.failure("Insufficient funds", senderBalance, recipientBalance);
                            }

                            final MoneyAmount recipientBalance = this.balanceOf(this.economyAccountRepository.findByPlayerId(connection, recipientId));
                            final MoneyAmount newSenderBalance = senderBalance.subtract(amount);
                            final MoneyAmount newRecipientBalance = recipientBalance.add(amount);

                            if (!this.economyAccountRepository.updateIfBalanceMatches(connection, senderId, senderBalance, newSenderBalance, now)) {
                                throw new ConcurrentBalanceUpdateException();
                            }
                            if (!this.economyAccountRepository.updateIfBalanceMatches(connection, recipientId, recipientBalance, newRecipientBalance, now)) {
                                throw new ConcurrentBalanceUpdateException();
                            }

                            this.economyLedgerRepository.insert(connection, new EconomyLedgerEntry(
                                    senderId,
                                    EconomyReason.PLAYER_PAY_SEND,
                                    MoneyAmount.ofMinor(-amount.minorUnits()),
                                    newSenderBalance,
                                    recipientId,
                                    referenceType,
                                    referenceId,
                                    now
                            ));

                            this.economyLedgerRepository.insert(connection, new EconomyLedgerEntry(
                                    recipientId,
                                    EconomyReason.PLAYER_PAY_RECEIVE,
                                    amount,
                                    newRecipientBalance,
                                    senderId,
                                    referenceType,
                                    referenceId,
                                    now
                            ));
                            return EconomyTransferResult.success(newSenderBalance, newRecipientBalance);
                        });

                        if (!result.success()) {
                            return result;
                        }

                        this.balanceCache.put(senderId, result.senderBalance(), now, this.balanceCache.isOnline(senderId));
                        this.balanceCache.put(recipientId, result.recipientBalance(), now, this.balanceCache.isOnline(recipientId));
                        this.baltopService.invalidate();
                        return result;
                    } catch (final ConcurrentBalanceUpdateException ignored) {
                        // Retry against the latest shared-database state.
                    }
                }

                return EconomyTransferResult.failure(
                        "Balance changed during update, please try again.",
                        this.getBalanceForSensitiveOperation(senderId),
                        this.getBalance(recipientId)
                );
            }
        }
    }

    @Override
    public void invalidate(final UUID playerId) {
        this.balanceCache.evict(playerId);
    }

    private MoneyAmount balanceOf(final EconomyAccountRecord account) {
        return account == null ? MoneyAmount.zero() : account.balance();
    }

    private MoneyAmount loadBalance(final UUID playerId, final boolean forceRefresh) {
        final Object lock = this.lockFor(playerId);
        synchronized (lock) {
            return this.loadBalanceLocked(playerId, forceRefresh);
        }
    }

    private MoneyAmount loadBalanceLocked(final UUID playerId, final boolean forceRefresh) {
        final long now = this.now();
        final BalanceCache.CachedBalance cachedBalance = this.balanceCache.get(playerId).orElse(null);
        if (cachedBalance != null && !forceRefresh) {
            return cachedBalance.balance();
        }

        final EconomyAccountRecord account = this.economyAccountRepository.findByPlayerId(playerId);
        final MoneyAmount resolvedBalance = account == null ? MoneyAmount.zero() : account.balance();
        final boolean online = cachedBalance != null && cachedBalance.online();
        this.balanceCache.put(playerId, resolvedBalance, now, online);
        return resolvedBalance;
    }

    private boolean isStale(final BalanceCache.CachedBalance cachedBalance, final long nowEpochSecond) {
        final int ttlSeconds = cachedBalance.online()
                ? Math.max(1, this.economyConfig.onlineRefreshSeconds())
                : Math.max(1, this.economyConfig.offlineCacheSeconds());
        return cachedBalance.refreshedAtEpochSecond() + ttlSeconds <= nowEpochSecond;
    }

    private Object lockFor(final UUID playerId) {
        return this.playerLocks.computeIfAbsent(playerId, ignored -> new Object());
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private boolean hasPlayerName(final String playerName) {
        return playerName != null && !playerName.isBlank();
    }

    private int order(final UUID left, final UUID right) {
        return left.toString().compareTo(right.toString());
    }

    private static final class ConcurrentBalanceUpdateException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
