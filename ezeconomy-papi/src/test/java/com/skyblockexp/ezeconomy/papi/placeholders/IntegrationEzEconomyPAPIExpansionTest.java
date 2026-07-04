package com.skyblockexp.ezeconomy.papi.placeholders;

import org.bukkit.OfflinePlayer;
import com.skyblockexp.ezeconomy.papi.testhelpers.TestPlayerFakes;
import org.junit.jupiter.api.Test;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;

import java.lang.reflect.Field;
import java.util.*;
import com.skyblockexp.ezeconomy.cache.ExpiringCache;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationEzEconomyPAPIExpansionTest {

    static class StubStorage implements StorageProvider {
        private final Map<UUID, Double> balances = new HashMap<>();

        public void put(UUID u, double v) { balances.put(u, v); }

        @Override public void init() {}
        @Override public void load() {}
        @Override public void save() {}
        @Override public double getBalance(UUID uuid, String currency) { return balances.getOrDefault(uuid, 0.0); }
        @Override public void setBalance(UUID uuid, String currency, double amount) { balances.put(uuid, amount); }
        @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
        @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return Collections.emptyList(); }
        @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { Double v = balances.get(uuid); if (v == null || v < amount) return false; balances.put(uuid, v - amount); return true; }
        @Override public void deposit(UUID uuid, String currency, double amount) { balances.put(uuid, balances.getOrDefault(uuid,0.0)+amount); }
        @Override public Map<UUID, Double> getAllBalances(String currency) { return new HashMap<>(balances); }
        @Override public java.util.Set<String> cleanupOrphanedPlayers() { return Collections.emptySet(); }
        @Override public boolean isConnected() { return true; }
        @Override public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) { return com.skyblockexp.ezeconomy.storage.TransferResult.failure(0,0); }
        @Override public void shutdown() {}
        @Override public boolean createBank(String name, UUID owner) { return false; }
        @Override public boolean deleteBank(String name) { return false; }
        @Override public boolean bankExists(String name) { return false; }
        @Override public double getBankBalance(String name, String currency) { return 0; }
        @Override public void setBankBalance(String name, String currency, double amount) {}
        @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
        @Override public void depositBank(String name, String currency, double amount) {}
        @Override public Set<String> getBanks() { return Collections.emptySet(); }
        @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
        @Override public boolean isBankMember(String name, UUID uuid) { return false; }
        @Override public boolean addBankMember(String name, UUID uuid) { return false; }
        @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
        @Override public Set<UUID> getBankMembers(String name) { return Collections.emptySet(); }
        @Override
        public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) {
            if (!balances.containsKey(uuid)) return null;
            return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, "TestPlayer", "TestPlayer");
        }
    }

    static class StubEzEconomy implements com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy {
        private final StubStorage storage = new StubStorage();

        @Override
        public StorageProvider getStorageOrWarn() { return storage; }

        @Override
        public String getDefaultCurrency() { return "dollar"; }

        @Override
        public String format(double amount, String currency) { return String.format(java.util.Locale.US, "%.2f %s", amount, currency); }

        @Override
        public String formatShort(double amount, String currency) { return String.format(java.util.Locale.US, "%.1f %s", amount, currency); }

        @Override
        public String getCurrencySymbol(String currency) { return "$"; }

        @Override
        public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
    }

    @Test
    public void integrationPlaceholderResponsesWithoutBukkit() throws Exception {
        StubEzEconomy stub = new StubEzEconomy();
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = stub;

        UUID u = UUID.randomUUID();
        stub.getStorageOrWarn().setBalance(u, "dollar", 123.45);
        stub.getStorageOrWarn().setBalance(UUID.randomUUID(), "dollar", 10.0);

        OfflinePlayer fakePlayer = TestPlayerFakes.fakeOfflinePlayer(u);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);

        String balance = expansion.onPlaceholderRequest(fakePlayer, "balance");
        assertNotNull(balance);
        assertTrue(balance.contains("123.45"));

        String formatted = expansion.onPlaceholderRequest(fakePlayer, "balance_formatted");
        assertNotNull(formatted);
        assertTrue(formatted.contains("123.45") || formatted.contains("123.5"));

        String formattedDollar = expansion.onPlaceholderRequest(fakePlayer, "balance_formatted_dollar");
        assertNotNull(formattedDollar);
        assertTrue(formattedDollar.contains("123.45") || formattedDollar.contains("123.5"));

        String shortVal = expansion.onPlaceholderRequest(fakePlayer, "balance_short");
        assertNotNull(shortVal);
        assertTrue(shortVal.length() > 0);
        String shortDollar = expansion.onPlaceholderRequest(fakePlayer, "balance_short_dollar");
        assertNotNull(shortDollar);
        assertTrue(shortDollar.length() > 0);

        String symbol = expansion.onPlaceholderRequest(null, "symbol_dollar");
        assertNotNull(symbol);

        Field cacheField = com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.class.getDeclaredField("topCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.skyblockexp.ezeconomy.cache.CacheProvider<String, String> topCache = (com.skyblockexp.ezeconomy.cache.CacheProvider<String, String>) cacheField.get(expansion);
        assertNotNull(topCache);
        topCache.put("top:dollar:1", "player - 123.45 dollar", 10000L);

        String top = expansion.onPlaceholderRequest(null, "top_1_dollar");
        assertEquals("player - 123.45 dollar", top);

        String bank = expansion.onPlaceholderRequest(null, "bank_test_dollar");
        assertNotNull(bank);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }
}
