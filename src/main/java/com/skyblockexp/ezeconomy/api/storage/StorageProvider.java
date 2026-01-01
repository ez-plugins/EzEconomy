package com.skyblockexp.ezeconomy.api.storage;

import com.skyblockexp.ezeconomy.storage.TransferResult;
import com.skyblockexp.ezeconomy.storage.TransferLockManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public interface StorageProvider {
    /**
     * Gets the balance for a player and currency.
     * @param uuid Player UUID
     * @param currency Currency identifier
     * @return Player's balance for the given currency
     */
    double getBalance(UUID uuid, String currency);

    /**
     * Sets the balance for a player and currency.
     * @param uuid Player UUID
     * @param currency Currency identifier
     * @param amount New balance
     */
    void setBalance(UUID uuid, String currency, double amount);

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
        ReentrantLock lock = TransferLockManager.getLock(fromUuid);
        lock.lock();
        try {
            double fromBalance = getBalance(fromUuid, currency);
            if (fromBalance < debitAmount) {
                double toBalance = getBalance(toUuid, currency);
                return TransferResult.failure(fromBalance, toBalance);
            }
            if (!tryWithdraw(fromUuid, currency, debitAmount)) {
                double refreshedFrom = getBalance(fromUuid, currency);
                double toBalance = getBalance(toUuid, currency);
                return TransferResult.failure(refreshedFrom, toBalance);
            }
            if (creditAmount > 0) {
                deposit(toUuid, currency, creditAmount);
            }
            double updatedFrom = getBalance(fromUuid, currency);
            double updatedTo = getBalance(toUuid, currency);
            return TransferResult.success(updatedFrom, updatedTo);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the balance for a player using the legacy single-currency fallback.
     * @deprecated Use getBalance(UUID, String) instead.
     */
    @Deprecated
    default double getBalance(UUID uuid) { return getBalance(uuid, "dollar"); }

    /**
     * Sets the balance for a player using the legacy single-currency fallback.
     * @deprecated Use setBalance(UUID, String, double) instead.
     */
    @Deprecated
    default void setBalance(UUID uuid, double amount) { setBalance(uuid, "dollar", amount); }

    /**
     * Attempts to withdraw from a player using the legacy single-currency fallback.
     * @deprecated Use tryWithdraw(UUID, String, double) instead.
     */
    @Deprecated
    default boolean tryWithdraw(UUID uuid, double amount) { return tryWithdraw(uuid, "dollar", amount); }

    /**
     * Deposits to a player using the legacy single-currency fallback.
     * @deprecated Use deposit(UUID, String, double) instead.
     */
    @Deprecated
    default void deposit(UUID uuid, double amount) { deposit(uuid, "dollar", amount); }

    /**
     * Gets all player balances using the legacy single-currency fallback.
     * @deprecated Use getAllBalances(String) instead.
     */
    @Deprecated
    default Map<UUID, Double> getAllBalances() { return getAllBalances("dollar"); }

    /**
     * Shuts down the storage provider and closes any open resources.
     */
    void shutdown();

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
     * Gets the balance for a bank using the legacy single-currency fallback.
     * @deprecated Use getBankBalance(String, String) instead.
     */
    @Deprecated
    default double getBankBalance(String name) { return getBankBalance(name, "dollar"); }

    /**
     * Sets the balance for a bank using the legacy single-currency fallback.
     * @deprecated Use setBankBalance(String, String, double) instead.
     */
    @Deprecated
    default void setBankBalance(String name, double amount) { setBankBalance(name, "dollar", amount); }

    /**
     * Attempts to withdraw from a bank using the legacy single-currency fallback.
     * @deprecated Use tryWithdrawBank(String, String, double) instead.
     */
    @Deprecated
    default boolean tryWithdrawBank(String name, double amount) { return tryWithdrawBank(name, "dollar", amount); }

    /**
     * Deposits to a bank using the legacy single-currency fallback.
     * @deprecated Use depositBank(String, String, double) instead.
     */
    @Deprecated
    default void depositBank(String name, double amount) { depositBank(name, "dollar", amount); }
}
