package com.skyblockexp.ezeconomy.papi.formatting;

import org.bukkit.OfflinePlayer;
import com.skyblockexp.ezeconomy.papi.testhelpers.TestPlayerFakes;
import org.junit.jupiter.api.Test;

import java.util.UUID;
 

import static org.junit.jupiter.api.Assertions.*;

public class BalanceFormattedShortEdgeCasesTest {

    static class StubStorage implements com.skyblockexp.ezeconomy.api.storage.StorageProvider {
        private double val = 0d;
        public void set(double v) { val = v; }
        @Override public void init() {}
        @Override public void load() {}
        @Override public void save() {}
        @Override public double getBalance(UUID uuid, String currency) { return val; }
        @Override public void setBalance(UUID uuid, String currency, double amount) { val = amount; }
        @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
        @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return java.util.Collections.emptyList(); }
        @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { if (val < amount) return false; val -= amount; return true; }
        @Override public void deposit(UUID uuid, String currency, double amount) { val += amount; }
        @Override public java.util.Map<UUID, Double> getAllBalances(String currency) { return java.util.Collections.emptyMap(); }
        @Override public void shutdown() {}
        @Override public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) { return null; }
        @Override public boolean createBank(String name, UUID owner) { return false; }
        @Override public boolean deleteBank(String name) { return false; }
        @Override public boolean bankExists(String name) { return false; }
        @Override public double getBankBalance(String name, String currency) { return 0; }
        @Override public void setBankBalance(String name, String currency, double amount) {}
        @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
        @Override public void depositBank(String name, String currency, double amount) {}
        @Override public java.util.Set<String> getBanks() { return java.util.Collections.emptySet(); }
        @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
        @Override public boolean isBankMember(String name, UUID uuid) { return false; }
        @Override public boolean addBankMember(String name, UUID uuid) { return false; }
        @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
        @Override public java.util.Set<UUID> getBankMembers(String name) { return java.util.Collections.emptySet(); }
        @Override public java.util.Set<String> cleanupOrphanedPlayers() { return java.util.Collections.emptySet(); }
        @Override public boolean isConnected() { return true; }
        @Override public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) { return com.skyblockexp.ezeconomy.storage.TransferResult.failure(0,0); }
    }

    static class StubEz implements com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy {
        final StubStorage s = new StubStorage();
        @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return s; }
        @Override public String getDefaultCurrency() { return "euro"; }
        @Override public String format(double amount, String currency) { return String.format(java.util.Locale.US, "%.2f %s", amount, currency); }
        @Override public String formatShort(double amount, String currency) { return String.format(java.util.Locale.US, "%.1f%s", amount >= 1000 ? amount/1000.0 : amount, amount >= 1000 ? "K" : ""); }
        @Override public String getCurrencySymbol(String currency) { return "€"; }
        @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
    }

    private OfflinePlayer offlinePlayer(UUID id) {
        return TestPlayerFakes.fakeOfflinePlayer(id);
    }

    @Test
    public void balanceFormatted_and_short_edge_cases_useDefaultWhenBlankOrMissing() {
        StubEz stub = new StubEz();
        stub.s.set(50.0);
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = stub;

        UUID u = UUID.randomUUID();
        OfflinePlayer p = offlinePlayer(u);
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion exp = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);

        String f1 = exp.onPlaceholderRequest(p, "balance_formatted");
        assertTrue(f1.contains("50.00") && f1.contains("euro") || f1.contains("50.00"));

        String s1 = exp.onPlaceholderRequest(p, "balance_short");
        assertNotNull(s1);

        stub.s.set(123.45);
        String f2 = exp.onPlaceholderRequest(p, "balance_formatted_dollar");
        assertTrue(f2.contains("123.45") || f2.contains("123.5"));

        String s2 = exp.onPlaceholderRequest(p, "balance_short_dollar");
        assertNotNull(s2);

        stub.s.set(7.0);
        String f3 = exp.onPlaceholderRequest(p, "balance_formatted_");
        assertTrue(f3.contains("7.00") || f3.contains("7.0"));

        String s3 = exp.onPlaceholderRequest(p, "balance_short_");
        assertNotNull(s3);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }

    @Test
    public void balanceFormatted_and_short_returnZeroWhenPlayerNull() {
        StubEz stub = new StubEz();
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = stub;
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion exp = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);

        assertEquals("0", exp.onPlaceholderRequest(null, "balance_formatted"));
        assertEquals("0", exp.onPlaceholderRequest(null, "balance_short"));

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }
}
