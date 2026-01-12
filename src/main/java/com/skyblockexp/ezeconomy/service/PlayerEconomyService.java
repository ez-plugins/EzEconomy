package com.skyblockexp.ezeconomy.service;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import com.skyblockexp.ezeconomy.storage.TransferResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for player economy operations (balances, transactions, transfers).
 */
public class PlayerEconomyService {
    private final StorageProvider storageProvider;

    public PlayerEconomyService(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    public double getBalance(UUID uuid, String currency) {
        return storageProvider.getBalance(uuid, currency);
    }

    public boolean deposit(UUID uuid, String currency, double amount) {
        storageProvider.deposit(uuid, currency, amount);
        return true;
    }

    public boolean withdraw(UUID uuid, String currency, double amount) {
        return storageProvider.tryWithdraw(uuid, currency, amount);
    }

    public List<Transaction> getTransactions(UUID uuid, String currency) {
        return storageProvider.getTransactions(uuid, currency);
    }

    public Map<UUID, Double> getAllBalances(String currency) {
        return storageProvider.getAllBalances(currency);
    }

    public TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) {
        return storageProvider.transfer(fromUuid, toUuid, currency, amount);
    }

    public TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) {
        return storageProvider.transfer(fromUuid, toUuid, currency, debitAmount, creditAmount);
    }
}
