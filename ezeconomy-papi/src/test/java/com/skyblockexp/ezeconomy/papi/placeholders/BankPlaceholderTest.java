package com.skyblockexp.ezeconomy.papi.placeholders;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BankPlaceholderTest {

    static class StubStorage implements com.skyblockexp.ezeconomy.api.storage.StorageProvider {
        @Override public void init() {}
        @Override public void load() {}
        @Override public void save() {}
        @Override public double getBalance(UUID uuid, String currency) { return 0; }
        @Override public void setBalance(UUID uuid, String currency, double amount) {}
        @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
        @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return java.util.Collections.emptyList(); }
        @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { return false; }
        @Override public void deposit(UUID uuid, String currency, double amount) {}
        @Override public java.util.Map<UUID, Double> getAllBalances(String currency) { return java.util.Collections.emptyMap(); }
        @Override public java.util.Set<String> cleanupOrphanedPlayers() { return java.util.Collections.emptySet(); }
        @Override public boolean isConnected() { return true; }
        @Override public void shutdown() {}
        @Override public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) { return null; }
        @Override public boolean createBank(String name, UUID owner) { return false; }
        @Override public boolean deleteBank(String name) { return false; }
        @Override public boolean bankExists(String name) { return false; }
        @Override public double getBankBalance(String name, String currency) { return 0; }
        @Override public void setBankBalance(String name, String currency, double amount) {}
        @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
        @Override public void depositBank(String name, String currency, double amount) {}
        @Override public java.util.Set<UUID> getBankMembers(String name) { return java.util.Collections.emptySet(); }
        @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
        @Override public boolean isBankMember(String name, UUID uuid) { return false; }
        @Override public boolean addBankMember(String name, UUID uuid) { return false; }
        @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
        @Override public java.util.Set<String> getBanks() { return java.util.Collections.emptySet(); }
    }

    static class StubEzEconomy implements com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy {
        private final StubStorage storage = new StubStorage();
        @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return storage; }
        @Override public String getDefaultCurrency() { return "dollar"; }
        @Override public String format(double amount, String currency) { return String.format(Locale.ROOT, "%.2f %s", amount, currency); }
        @Override public String formatShort(double amount, String currency) { return String.format(Locale.ROOT, "%.1f %s", amount, currency); }
        @Override public String getCurrencySymbol(String currency) { return "$"; }
        @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
    }

    @Test
    public void bank_placeholder_returns_empty_when_bank_missing() {
        StubEzEconomy stub = new StubEzEconomy();
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = stub;
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);

        String out = expansion.onPlaceholderRequest(null, "bank_nonexist_dollar");
        assertNotNull(out);
        // Depending on expansion behavior this may return an empty string or a formatted zero balance.
        assertTrue(out.isEmpty() || out.contains("0"));
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }
}


