package com.skyblockexp.ezeconomy.storage;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public interface StorageProvider {
    // Player balances
    double getBalance(UUID uuid, String currency);
    void setBalance(UUID uuid, String currency, double amount);
    boolean tryWithdraw(UUID uuid, String currency, double amount);
    void deposit(UUID uuid, String currency, double amount);
    Map<UUID, Double> getAllBalances(String currency);

    default TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) {
        return transfer(fromUuid, toUuid, currency, amount, amount);
    }

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

    // Legacy single-currency fallback
    @Deprecated
    default double getBalance(UUID uuid) { return getBalance(uuid, "dollar"); }
    @Deprecated
    default void setBalance(UUID uuid, double amount) { setBalance(uuid, "dollar", amount); }
    @Deprecated
    default boolean tryWithdraw(UUID uuid, double amount) { return tryWithdraw(uuid, "dollar", amount); }
    @Deprecated
    default void deposit(UUID uuid, double amount) { deposit(uuid, "dollar", amount); }
    @Deprecated
    default Map<UUID, Double> getAllBalances() { return getAllBalances("dollar"); }

    void shutdown();

    // --- Bank support ---
    boolean createBank(String name, UUID owner);
    boolean deleteBank(String name);
    boolean bankExists(String name);
    double getBankBalance(String name, String currency);
    void setBankBalance(String name, String currency, double amount);
    boolean tryWithdrawBank(String name, String currency, double amount);
    void depositBank(String name, String currency, double amount);
    Set<String> getBanks();
    boolean isBankOwner(String name, UUID uuid);
    boolean isBankMember(String name, UUID uuid);
    boolean addBankMember(String name, UUID uuid);
    boolean removeBankMember(String name, UUID uuid);
    Set<UUID> getBankMembers(String name);

    // Legacy single-currency fallback for banks
    @Deprecated
    default double getBankBalance(String name) { return getBankBalance(name, "dollar"); }
    @Deprecated
    default void setBankBalance(String name, double amount) { setBankBalance(name, "dollar", amount); }
    @Deprecated
    default boolean tryWithdrawBank(String name, double amount) { return tryWithdrawBank(name, "dollar", amount); }
    @Deprecated
    default void depositBank(String name, double amount) { depositBank(name, "dollar", amount); }
}
