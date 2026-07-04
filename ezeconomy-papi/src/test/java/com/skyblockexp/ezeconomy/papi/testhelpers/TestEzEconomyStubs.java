package com.skyblockexp.ezeconomy.papi.testhelpers;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.dto.EconomyPlayer;
import com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

public class TestEzEconomyStubs {

    public static class SimpleStorageProvider implements StorageProvider {
        private final Map<UUID, Double> balances = new HashMap<>();
        private final Map<String, Double> banks = new HashMap<>();
        private final Map<UUID, com.skyblockexp.ezeconomy.dto.EconomyPlayer> players = new HashMap<>();

        @Override public void init() {}
        @Override public void load() {}
        @Override public void save() {}
        @Override public double getBalance(UUID uuid, String currency) { return balances.getOrDefault(uuid, 0d); }
        @Override public void setBalance(UUID uuid, String currency, double amount) { balances.put(uuid, amount); }
        @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
        @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return java.util.Collections.emptyList(); }
        @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { Double b = balances.getOrDefault(uuid, 0d); if (b < amount) return false; balances.put(uuid, b - amount); return true; }
        @Override public void deposit(UUID uuid, String currency, double amount) { balances.put(uuid, balances.getOrDefault(uuid, 0d) + amount); }
        @Override public java.util.Map<UUID, Double> getAllBalances(String currency) { return new HashMap<>(balances); }
        @Override public java.util.Set<String> cleanupOrphanedPlayers() { return Collections.emptySet(); }
        @Override public boolean isConnected() { return true; }
        @Override public void shutdown() {}
        public void putPlayer(UUID uuid, EconomyPlayer p) { players.put(uuid, p); }
        @Override public EconomyPlayer getPlayer(UUID uuid) { return players.get(uuid); }
        @Override public boolean createBank(String name, UUID owner) { return false; }
        @Override public boolean deleteBank(String name) { return false; }
        @Override public boolean bankExists(String name) { return banks.containsKey(name); }
        @Override public double getBankBalance(String name, String currency) { return banks.getOrDefault(name, 0d); }
        @Override public void setBankBalance(String name, String currency, double amount) { banks.put(name, amount); }
        @Override public boolean tryWithdrawBank(String name, String currency, double amount) { Double b = banks.getOrDefault(name, 0d); if (b < amount) return false; banks.put(name, b - amount); return true; }
        @Override public void depositBank(String name, String currency, double amount) { banks.put(name, banks.getOrDefault(name, 0d) + amount); }
        @Override public java.util.Set<String> getBanks() { return banks.keySet(); }
        @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
        @Override public boolean isBankMember(String name, UUID uuid) { return false; }
        @Override public boolean addBankMember(String name, UUID uuid) { return false; }
        @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
        @Override public java.util.Set<UUID> getBankMembers(String name) { return Collections.emptySet(); }
    }

    public static class SimpleTestEz implements EzEconomyPAPIExpansion.TestEzEconomy {
        private final StorageProvider storage;
        private final String defaultCurrency;

        public SimpleTestEz(StorageProvider storage, String defaultCurrency) {
            this.storage = storage;
            this.defaultCurrency = defaultCurrency;
        }

        @Override public StorageProvider getStorageOrWarn() { return storage; }
        @Override public String getDefaultCurrency() { return defaultCurrency; }
        @Override public String format(double amount, String currency) { return String.format(Locale.ROOT, "%.2f %s", amount, currency); }
        @Override public String formatShort(double amount, String currency) { if (amount >= 1000) return String.format(Locale.ROOT, "%.1fK %s", amount / 1000d, currency); return format(amount, currency); }
        @Override public String getCurrencySymbol(String currency) { return null; }
        @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
    }
}
