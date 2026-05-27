package com.skyblockexp.ezeconomy.api.storage;

import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageInitException;
import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageLoadException;
import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageSaveException;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import com.skyblockexp.ezeconomy.dto.EconomyPlayer;
import com.skyblockexp.ezeconomy.storage.TransferLockManager;
import com.skyblockexp.ezeconomy.storage.TransferResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public interface StorageProvider {
    /**
     * Setup and initialize the storage provider.
     * @throws StorageInitException
     */
    void init() throws StorageInitException;
    
    /**
     * Build database connection and load any necessary data.
     * @throws StorageLoadException
     */
    void load() throws StorageLoadException;

    /**
     * Save any in-memory data to the storage backend.
     * @throws StorageSaveException
     */
    void save() throws StorageSaveException;
    
    /**
     * Gets the balance for a player and currency.
     * @param uuid Player UUID
     * @param currency Currency identifier
     * @return Player's balance for the given currency
     */
    double getBalance(UUID uuid, String currency);

    /**
     * Checks whether a record exists for the given player UUID in the storage backend.
     * <p>
     * Providers should override this with an efficient implementation. The default
     * implementation falls back to checking whether {@link #getBalance(UUID)} returns
     * a non-zero value which is not perfectly accurate for zero-balance players, so
     * overriding is recommended.
     * </p>
     * @param uuid Player UUID to check
     * @return true if the storage backend contains a record for the player
     */
    default boolean playerExists(UUID uuid) {
        return getBalance(uuid) != 0.0;
    }

    /**
     * Sets the balance for a player and currency.
     * @param uuid Player UUID
     * @param currency Currency identifier
     * @param amount New balance
     */
    void setBalance(UUID uuid, String currency, double amount);

    /**
     * Logs a transaction for a player and currency.
     * @param transaction Transaction object to log
     */
    void logTransaction(Transaction transaction);

    /**
     * Retrieves the transaction history for a player and currency.
     * @param uuid Player UUID
     * @param currency Currency identifier
     * @return List of transactions
     */
    List<Transaction> getTransactions(UUID uuid, String currency);

    /**
     * Attempts to withdraw an amount from a player's balance for a currency.
     * @param uuid Player UUID
     * @param currency Currency identifier
     * @param amount Amount to withdraw
     * @return true if successful, false if insufficient funds
     */
    boolean tryWithdraw(UUID uuid, String currency, double amount);

    /**
     * Deposits an amount to a player's balance for a currency.
     * @param uuid Player UUID
     * @param currency Currency identifier
     * @param amount Amount to deposit
     */
    void deposit(UUID uuid, String currency, double amount);

    /**
     * Gets all player balances for a currency.
     * @param currency Currency identifier
     * @return Map of UUID to balance
     */
    Map<UUID, Double> getAllBalances(String currency);

    /**
     * Removes balances for UUIDs that do not resolve to a known player.
     * Default: no-op, returns empty set. Override if needed.
     * @return Set of removed UUIDs as strings
     */
    default java.util.Set<String> cleanupOrphanedPlayers() {
        return java.util.Collections.emptySet();
    }

    /**
     * Resolves a player UUID by their name from the storage backend.
     * @param name player name (case-insensitive)
     * @return UUID if found, null otherwise
     */
    default java.util.UUID resolvePlayerByName(String name) {
        return null;
    }

    /**
     * Persists player info (UUID, name, display name) to the storage backend.
     * Called on player join to keep the player lookup table up to date.
     * @param uuid player UUID
     * @param name player name
     * @param displayName player display name
     */
    default void persistPlayerInfo(java.util.UUID uuid, String name, String displayName) {
    }

    /**
     * Inserts a pending notification message for a player who is currently offline.
     * @param targetUuid target player UUID
     * @param message formatted notification message
     */
    default void insertPendingNotification(java.util.UUID targetUuid, String message) {
    }

    /**
     * Retrieves and removes all pending notification messages for a player.
     * Called when the player joins to deliver queued messages.
     * @param targetUuid target player UUID
     * @return list of pending messages, empty if none
     */
    default java.util.List<String> pollPendingNotifications(java.util.UUID targetUuid) {
        return java.util.Collections.emptyList();
    }

    /**
     * Removes pending notifications older than the specified timestamp.
     * @param olderThanMillis epoch milliseconds; notifications older than this are removed
     */
    default void cleanupOldNotifications(long olderThanMillis) {
    }

    /**
     * Returns true if the storage provider is currently connected to its backend (database, file, etc).
     * Default: always true (for file-based providers). Override for real DB status.
     */
    default boolean isConnected() {
        return false;
    }

    /**
     * Transfers an amount from one player to another for a currency.
     * @param fromUuid Sender UUID
     * @param toUuid Recipient UUID
     * @param currency Currency identifier
     * @param amount Amount to transfer
     * @return TransferResult with updated balances and status
     */
    default TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) {
        return transfer(fromUuid, toUuid, currency, amount, amount);
    }

    /**
     * Transfers a custom debit and credit amount between two players for a currency.
     * @param fromUuid Sender UUID
     * @param toUuid Recipient UUID
     * @param currency Currency identifier
     * @param debitAmount Amount to withdraw from sender
     * @param creditAmount Amount to deposit to recipient
     * @return TransferResult with updated balances and status
     */
    default TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) {
        if (debitAmount < 0 || creditAmount < 0) {
            return TransferResult.failure(getBalance(fromUuid, currency), getBalance(toUuid, currency));
        }
        // Prefer plugin-provided LockManager for distributed locking if available
        com.skyblockexp.ezeconomy.core.EzEconomyPlugin inst = com.skyblockexp.ezeconomy.core.EzEconomyPlugin.getInstance();
        com.skyblockexp.ezeconomy.lock.LockManager lm = inst == null ? null : inst.getLockManager();
        if (lm == null) {
            return transferWithLocalLocks(fromUuid, toUuid, currency, debitAmount, creditAmount);
        }

        // Acquire distributed locks in canonical order
        UUID[] ordered = new UUID[]{fromUuid, toUuid};
        if (fromUuid.compareTo(toUuid) > 0) ordered = new UUID[]{toUuid, fromUuid};
        String[] tokens = null;
        try {
            tokens = lm.acquireOrdered(ordered, inst.getLockTtlMs(), inst.getLockRetryMs(), inst.getLockMaxAttempts());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (tokens == null) {
            // Couldn't acquire distributed locks; fall back to local without recursion.
            return transferWithLocalLocks(fromUuid, toUuid, currency, debitAmount, creditAmount);
        }

        try {
            double fromBalance = getBalance(fromUuid, currency);
            double toBalance = getBalance(toUuid, currency);
            if (fromBalance < debitAmount) return TransferResult.failure(fromBalance, toBalance);
            if (!tryWithdraw(fromUuid, currency, debitAmount)) {
                return TransferResult.failure(getBalance(fromUuid, currency), getBalance(toUuid, currency));
            }
            if (creditAmount > 0) deposit(toUuid, currency, creditAmount);
            return TransferResult.success(getBalance(fromUuid, currency), getBalance(toUuid, currency));
        } finally {
            lm.releaseOrdered(ordered, tokens);
        }
    }

    private TransferResult transferWithLocalLocks(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) {
        UUID first = fromUuid.compareTo(toUuid) <= 0 ? fromUuid : toUuid;
        UUID second = fromUuid.compareTo(toUuid) <= 0 ? toUuid : fromUuid;
        ReentrantLock firstLock = TransferLockManager.getLock(first);
        ReentrantLock secondLock = (first.equals(second)) ? firstLock : TransferLockManager.getLock(second);
        firstLock.lock();
        if (!first.equals(second)) secondLock.lock();
        try {
            double fromBalance = getBalance(fromUuid, currency);
            if (fromBalance < debitAmount) return TransferResult.failure(fromBalance, getBalance(toUuid, currency));
            if (!tryWithdraw(fromUuid, currency, debitAmount)) {
                return TransferResult.failure(getBalance(fromUuid, currency), getBalance(toUuid, currency));
            }
            if (creditAmount > 0) deposit(toUuid, currency, creditAmount);
            return TransferResult.success(getBalance(fromUuid, currency), getBalance(toUuid, currency));
        } finally {
            if (!first.equals(second)) secondLock.unlock();
            firstLock.unlock();
        }
    }

    /**
     * <b>Legacy overload:</b> Gets the balance for a player using the default currency ("dollar").
     * <p>
     * This is a convenience overload for backwards compatibility with single-currency plugins.
     * Prefer {@link #getBalance(UUID, String)} for multi-currency support.
     * </p>
     * @deprecated Use {@link #getBalance(UUID, String)} for multi-currency support.
     */
    @Deprecated
    default double getBalance(UUID uuid) {
        return getBalance(uuid, "dollar");
    }

    /**
     * <b>Legacy overload:</b> Sets the balance for a player using the default currency ("dollar").
     * <p>
     * This is a convenience overload for backwards compatibility with single-currency plugins.
     * Prefer {@link #setBalance(UUID, String, double)} for multi-currency support.
     * </p>
     * @deprecated Use {@link #setBalance(UUID, String, double)} for multi-currency support.
     */
    @Deprecated
    default void setBalance(UUID uuid, double amount) {
        setBalance(uuid, "dollar", amount);
    }

    /**
     * <b>Legacy overload:</b> Attempts to withdraw from a player using the default currency ("dollar").
     * <p>
     * This is a convenience overload for backwards compatibility with single-currency plugins.
     * Prefer {@link #tryWithdraw(UUID, String, double)} for multi-currency support.
     * </p>
     * @deprecated Use {@link #tryWithdraw(UUID, String, double)} for multi-currency support.
     */
    @Deprecated
    default boolean tryWithdraw(UUID uuid, double amount) {
        return tryWithdraw(uuid, "dollar", amount);
    }

    /**
     * <b>Legacy overload:</b> Deposits to a player using the default currency ("dollar").
     * <p>
     * This is a convenience overload for backwards compatibility with single-currency plugins.
     * Prefer {@link #deposit(UUID, String, double)} for multi-currency support.
     * </p>
     * @deprecated Use {@link #deposit(UUID, String, double)} for multi-currency support.
     */
    @Deprecated
    default void deposit(UUID uuid, double amount) {
        deposit(uuid, "dollar", amount);
    }

    /**
     * <b>Legacy overload:</b> Gets all player balances using the default currency ("dollar").
     * <p>
     * This is a convenience overload for backwards compatibility with single-currency plugins.
     * Prefer {@link #getAllBalances(String)} for multi-currency support.
     * </p>
     * @deprecated Use {@link #getAllBalances(String)} for multi-currency support.
     */
    @Deprecated
    default Map<UUID, Double> getAllBalances() {
        return getAllBalances("dollar");
    }

    /**
     * Shuts down the storage provider and closes any open resources.
     */
    void shutdown();

    /**
     * Retrieve lightweight player information from the storage provider.
     * Implementations should return last-known name/displayName when available
     * to avoid an extra Bukkit/Mojang lookup.
     * @param uuid player UUID
     * @return EconomyPlayer with name/displayName or null if unknown
     */
    EconomyPlayer getPlayer(UUID uuid);

    /**
     * Creates a new bank with the given name and owner.
     * @param name Bank name
     * @param owner Owner UUID
     * @return true if created, false if already exists
     */
    boolean createBank(String name, UUID owner);

    /**
     * Deletes a bank by name.
     * @param name Bank name
     * @return true if deleted, false if not found
     */
    boolean deleteBank(String name);

    /**
     * Checks if a bank exists by name.
     * @param name Bank name
     * @return true if exists, false otherwise
     */
    boolean bankExists(String name);

    /**
     * Gets the balance for a bank and currency.
     * @param name Bank name
     * @param currency Currency identifier
     * @return Bank's balance for the given currency
     */
    double getBankBalance(String name, String currency);

    /**
     * Sets the balance for a bank and currency.
     * @param name Bank name
     * @param currency Currency identifier
     * @param amount New balance
     */
    void setBankBalance(String name, String currency, double amount);

    /**
     * Attempts to withdraw from a bank for a currency.
     * @param name Bank name
     * @param currency Currency identifier
     * @param amount Amount to withdraw
     * @return true if successful, false if insufficient funds
     */
    boolean tryWithdrawBank(String name, String currency, double amount);

    /**
     * Deposits to a bank for a currency.
     * @param name Bank name
     * @param currency Currency identifier
     * @param amount Amount to deposit
     */
    void depositBank(String name, String currency, double amount);

    /**
     * Gets all bank names.
     * @return Set of bank names
     */
    Set<String> getBanks();

    /**
     * Checks if a UUID is the owner of a bank.
     * @param name Bank name
     * @param uuid Player UUID
     * @return true if owner, false otherwise
     */
    boolean isBankOwner(String name, UUID uuid);

    /**
     * Checks if a UUID is a member of a bank.
     * @param name Bank name
     * @param uuid Player UUID
     * @return true if member, false otherwise
     */
    boolean isBankMember(String name, UUID uuid);

    /**
     * Adds a member to a bank.
     * @param name Bank name
     * @param uuid Player UUID
     * @return true if added, false if already a member
     */
    boolean addBankMember(String name, UUID uuid);

    /**
     * Removes a member from a bank.
     * @param name Bank name
     * @param uuid Player UUID
     * @return true if removed, false if not a member
     */
    boolean removeBankMember(String name, UUID uuid);

    /**
     * Gets all member UUIDs of a bank.
     * @param name Bank name
     * @return Set of member UUIDs
     */
    Set<UUID> getBankMembers(String name);

    /**
     * <b>Legacy overload:</b> Gets the balance for a bank using the default currency ("dollar").
     * <p>
     * This is a convenience overload for backwards compatibility with single-currency plugins.
     * Prefer {@link #getBankBalance(String, String)} for multi-currency support.
     * </p>
     * @deprecated Use {@link #getBankBalance(String, String)} for multi-currency support.
     */
    @Deprecated
    default double getBankBalance(String name) {
        return getBankBalance(name, "dollar");
    }

    /**
     * <b>Legacy overload:</b> Sets the balance for a bank using the default currency ("dollar").
     * <p>
     * This is a convenience overload for backwards compatibility with single-currency plugins.
     * Prefer {@link #setBankBalance(String, String, double)} for multi-currency support.
     * </p>
     * @deprecated Use {@link #setBankBalance(String, String, double)} for multi-currency support.
     */
    @Deprecated
    default void setBankBalance(String name, double amount) {
        setBankBalance(name, "dollar", amount);
    }

    /**
     * <b>Legacy overload:</b> Attempts to withdraw from a bank using the default currency ("dollar").
     * <p>
     * This is a convenience overload for backwards compatibility with single-currency plugins.
     * Prefer {@link #tryWithdrawBank(String, String, double)} for multi-currency support.
     * </p>
     * @deprecated Use {@link #tryWithdrawBank(String, String, double)} for multi-currency support.
     */
    @Deprecated
    default boolean tryWithdrawBank(String name, double amount) {
        return tryWithdrawBank(name, "dollar", amount);
    }

    /**
     * <b>Legacy overload:</b> Deposits to a bank using the default currency ("dollar").
     * <p>
     * This is a convenience overload for backwards compatibility with single-currency plugins.
     * Prefer {@link #depositBank(String, String, double)} for multi-currency support.
     * </p>
     * @deprecated Use {@link #depositBank(String, String, double)} for multi-currency support.
     */
    @Deprecated
    default void depositBank(String name, double amount) {
        depositBank(name, "dollar", amount);
    }
}
