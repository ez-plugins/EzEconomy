package com.skyblockexp.ezeconomy.service;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;

import java.util.Set;
import java.util.UUID;

/**
 * Service for bank-related economy operations.
 */
public class BankEconomyService {
    private final StorageProvider storageProvider;

    public BankEconomyService(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    public boolean createBank(String name, UUID owner) {
        return storageProvider.createBank(name, owner);
    }

    public boolean deleteBank(String name) {
        return storageProvider.deleteBank(name);
    }

    public boolean bankExists(String name) {
        return storageProvider.bankExists(name);
    }

    public double getBankBalance(String name, String currency) {
        return storageProvider.getBankBalance(name, currency);
    }

    public void setBankBalance(String name, String currency, double amount) {
        storageProvider.setBankBalance(name, currency, amount);
    }

    public boolean tryWithdrawBank(String name, String currency, double amount) {
        return storageProvider.tryWithdrawBank(name, currency, amount);
    }

    public void depositBank(String name, String currency, double amount) {
        storageProvider.depositBank(name, currency, amount);
    }

    public Set<String> getBanks() {
        return storageProvider.getBanks();
    }

    public boolean isBankOwner(String name, UUID uuid) {
        return storageProvider.isBankOwner(name, uuid);
    }

    public boolean isBankMember(String name, UUID uuid) {
        return storageProvider.isBankMember(name, uuid);
    }

    public boolean addBankMember(String name, UUID uuid) {
        return storageProvider.addBankMember(name, uuid);
    }

    public boolean removeBankMember(String name, UUID uuid) {
        return storageProvider.removeBankMember(name, uuid);
    }

    public Set<UUID> getBankMembers(String name) {
        return storageProvider.getBankMembers(name);
    }
}
