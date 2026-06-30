package com.skyblockexp.ezeconomy.papi.placeholders;

import org.bukkit.OfflinePlayer;
import com.skyblockexp.ezeconomy.papi.testhelpers.TestPlayerFakes;
import org.junit.jupiter.api.Test;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class BalancePlaceholderTest {

    static class StubStorage implements StorageProvider {
        private final Map<String, Double> balances = new HashMap<>();

        private String key(UUID u, String currency) { return u.toString()+"|"+currency; }
        public void put(UUID u, double v, String currency) { balances.put(key(u,currency), v); }

        @Override public void init() {}
        @Override public void load() {}
        @Override public void save() {}
        @Override public double getBalance(UUID uuid, String currency) { return balances.getOrDefault(key(uuid,currency), 0.0); }
        @Override public void setBalance(UUID uuid, String currency, double amount) { balances.put(key(uuid,currency), amount); }
        @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
        @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return Collections.emptyList(); }
        @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { Double v = balances.get(key(uuid,currency)); if (v == null || v < amount) return false; balances.put(key(uuid,currency), v - amount); return true; }
        @Override public void deposit(UUID uuid, String currency, double amount) { balances.put(key(uuid,currency), balances.getOrDefault(key(uuid,currency),0.0)+amount); }
        @Override public Map<UUID, Double> getAllBalances(String currency) { return Collections.emptyMap(); }
        @Override public java.util.Set<String> cleanupOrphanedPlayers() { return Collections.emptySet(); }
        @Override public boolean isConnected() { return true; }
        @Override public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) { return com.skyblockexp.ezeconomy.storage.TransferResult.failure(0,0); }
        @Override public void shutdown() {}
        @Override public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) { return null; }
        @Override public boolean createBank(String name, UUID owner) { return false; }
        @Override public boolean deleteBank(String name) { return false; }
        @Override public boolean bankExists(String name) { return false; }
        @Override public double getBankBalance(String name, String currency) { return 0; }
        @Override public void setBankBalance(String name, String currency, double amount) {}
        @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
        @Override public void depositBank(String name, String currency, double amount) {}
        @Override public Set<UUID> getBankMembers(String name) { return Collections.emptySet(); }
        @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
        @Override public boolean isBankMember(String name, UUID uuid) { return false; }
        @Override public boolean addBankMember(String name, UUID uuid) { return false; }
        @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
        @Override public Set<String> getBanks() { return Collections.emptySet(); }
    }

    static class StubEzEconomy implements com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy {
        private final StubStorage storage = new StubStorage();

        @Override
        public StorageProvider getStorageOrWarn() { return storage; }

        @Override
        public String getDefaultCurrency() { return "euro"; }

        @Override
        public String format(double amount, String currency) { return String.format(java.util.Locale.US, "%.2f %s", amount, currency); }

        @Override
        public String formatShort(double amount, String currency) { return String.format(java.util.Locale.US, "%.1f %s", amount, currency); }

        @Override
        public String getCurrencySymbol(String currency) { return "€"; }

        @Override
        public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
    }

    @Test
    public void placeholderUsesConfiguredDefaultCurrency() throws Exception {
        StubEzEconomy stub = new StubEzEconomy();
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = stub;

        UUID u = UUID.randomUUID();
        // set both euro and dollar balances for the player
        stub.getStorageOrWarn().setBalance(u, "euro", 50.0);
        stub.getStorageOrWarn().setBalance(u, "dollar", 123.45);

        OfflinePlayer fakePlayer = TestPlayerFakes.fakeOfflinePlayer(u);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);
        String balance = expansion.onPlaceholderRequest(fakePlayer, "balance");
        assertNotNull(balance);
        assertTrue(balance.contains("50.00") && balance.contains("euro"), "Expected balance formatted for euro, got: " + balance);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }

    @Test
    public void placeholderWithExplicitCurrencyResolvesThatCurrency() throws Exception {
        StubEzEconomy stub = new StubEzEconomy();
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = stub;

        UUID u = UUID.randomUUID();
        stub.getStorageOrWarn().setBalance(u, "euro", 50.0);
        stub.getStorageOrWarn().setBalance(u, "dollar", 123.45);

        OfflinePlayer fakePlayer = TestPlayerFakes.fakeOfflinePlayer(u);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);
        String balanceDollar = expansion.onPlaceholderRequest(fakePlayer, "balance_dollar");
        assertNotNull(balanceDollar);
        assertTrue(balanceDollar.contains("123.45") && balanceDollar.contains("dollar"), "Expected dollar balance, got: " + balanceDollar);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }
}
