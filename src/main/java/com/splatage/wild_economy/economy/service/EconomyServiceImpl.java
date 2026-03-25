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
            final long now = this.now();
            final MoneyAmount currentBalance = this.loadBalanceLocked(playerId, true);
            final MoneyAmount newBalance = currentBalance.add(amount);

            this.transactionRunner.run(connection -> {
                this.economyAccountRepository.createIfAbsent(connection, playerId, now);
                this.economyAccountRepository.upsert(connection, playerId, newBalance, now);
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
                return null;
            });

            this.balanceCache.put(playerId, newBalance, now, this.balanceCache.isOnline(playerId));
            this.baltopService.invalidate();
            return EconomyMutationResult.success(newBalance);
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

        final Object lock = this.lockFor(playerId);
        synchronized (lock) {
            final long now = this.now();
            final MoneyAmount currentBalance = this.loadBalanceLocked(playerId, true);
            if (currentBalance.minorUnits() < amount.minorUnits()) {
                return EconomyMutationResult.failure("Insufficient funds", currentBalance);
            }

            final MoneyAmount newBalance = currentBalance.subtract(amount);

            this.transactionRunner.run(connection -> {
                this.economyAccountRepository.createIfAbsent(connection, playerId, now);
                this.economyAccountRepository.upsert(connection, playerId, newBalance, now);
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
                return null;
            });

            this.balanceCache.put(playerId, newBalance, now, this.balanceCache.isOnline(playerId));
            this.baltopService.invalidate();
            return EconomyMutationResult.success(newBalance);
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
            final long now = this.now();
            final MoneyAmount currentBalance = this.loadBalanceLocked(playerId, false);
            final MoneyAmount delta = balance.subtract(currentBalance);

            this.transactionRunner.run(connection -> {
                this.economyAccountRepository.createIfAbsent(connection, playerId, now);
                this.economyAccountRepository.upsert(connection, playerId, balance, now);
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
                return null;
            });

            this.balanceCache.put(playerId, balance, now, this.balanceCache.isOnline(playerId));
            this.baltopService.invalidate();
            return EconomyMutationResult.success(balance);
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

        final UUID first = this.order(senderId, recipientId) <= 0 ? senderId : recipientId;
        final UUID second = first.equals(senderId) ? recipientId : senderId;

        final Object firstLock = this.lockFor(first);
        final Object secondLock = this.lockFor(second);

        synchronized (firstLock) {
            synchronized (secondLock) {
                final long now = this.now();

                final MoneyAmount senderBalance = this.loadBalanceLocked(senderId, true);
                if (senderBalance.minorUnits() < amount.minorUnits()) {
                    return EconomyTransferResult.failure("Insufficient funds", senderBalance, this.loadBalanceLocked(recipientId, true));
                }

                final MoneyAmount recipientBalance = this.loadBalanceLocked(recipientId, true);
                final MoneyAmount newSenderBalance = senderBalance.subtract(amount);
                final MoneyAmount newRecipientBalance = recipientBalance.add(amount);

                this.transactionRunner.run(connection -> {
                    this.economyAccountRepository.createIfAbsent(connection, senderId, now);
                    this.economyAccountRepository.createIfAbsent(connection, recipientId, now);
                    this.economyAccountRepository.upsert(connection, senderId, newSenderBalance, now);
                    this.economyAccountRepository.upsert(connection, recipientId, newRecipientBalance, now);

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
                    return null;
                });

                this.balanceCache.put(senderId, newSenderBalance, now, this.balanceCache.isOnline(senderId));
                this.balanceCache.put(recipientId, newRecipientBalance, now, this.balanceCache.isOnline(recipientId));
                this.baltopService.invalidate();
                return EconomyTransferResult.success(newSenderBalance, newRecipientBalance);
            }
        }
    }

    @Override
    public void invalidate(final UUID playerId) {
        this.balanceCache.evict(playerId);
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
        if (cachedBalance != null && !forceRefresh && !this.isStale(cachedBalance, now)) {
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
}
