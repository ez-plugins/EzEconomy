package com.skyblockexp.ezeconomy.papi;

import java.util.Locale;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.dto.EconomyPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.bukkit.OfflinePlayer;
import com.skyblockexp.ezeconomy.papi.testhelpers.TestPlayerFakes;

 
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class EzEconomyPAPIExpansionTest {

    @AfterEach
    public void tearDown() {
        EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }

    private OfflinePlayer offlinePlayer(UUID id) {
        return TestPlayerFakes.fakeOfflinePlayer(id);
    }

    @Test
    public void balance_returnsZeroWhenPlayerNull() {
        EzEconomyPAPIExpansion exp = new EzEconomyPAPIExpansion(null);
        // Provide a test hook so the code path doesn't attempt to access Bukkit static server
        EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyHelpers.formatting("dollar", "$");

        assertEquals("0", exp.onPlaceholderRequest((OfflinePlayer) null, "balance"));
    }

    @Test
    public void balance_usesTestEconomyStorage() {
        EzEconomyPAPIExpansion expansion = new EzEconomyPAPIExpansion(null);
        UUID id = UUID.randomUUID();

        StorageProvider stub = new SimpleStorageProvider();
        stub.setBalance(id, "dollar", 123.45);

        EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new EzEconomyPAPIExpansion.TestEzEconomy() {
            @Override
            public StorageProvider getStorageOrWarn() { return stub; }

            @Override
            public String getDefaultCurrency() { return "dollar"; }

            @Override
            public String format(double amount, String currency) { return String.format(Locale.ROOT, "$%.2f", amount); }

            @Override
            public String formatShort(double amount, String currency) { return String.format(Locale.ROOT, "$%.0f", amount); }

            @Override
            public String getCurrencySymbol(String currency) { return "$"; }

            @Override
            public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
        };

        OfflinePlayer p = offlinePlayer(id);
        String out = expansion.onPlaceholderRequest(p, "balance");
        assertEquals("$123.45", out);
    }

    @Test
    public void symbol_fallsBackToDollarWhenNullOrThrows() {
        EzEconomyPAPIExpansion expansion = new EzEconomyPAPIExpansion(null);

        EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new EzEconomyPAPIExpansion.TestEzEconomy() {
            @Override public StorageProvider getStorageOrWarn() { return null; }
            @Override public String getDefaultCurrency() { return "dollar"; }
            @Override public String format(double amount, String currency) { return ""; }
            @Override public String formatShort(double amount, String currency) { return ""; }
            @Override public String getCurrencySymbol(String currency) { throw new RuntimeException("boom"); }
            @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
        };

        String out = expansion.onPlaceholderRequest(null, "symbol_dollar");
        assertEquals("$", out);
    }

    @Test
    public void top_buildsAndCachesResult() {
        EzEconomyPAPIExpansion expansion = new EzEconomyPAPIExpansion(null);

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        SimpleStorageProvider stub = new SimpleStorageProvider();
        stub.setBalance(a, "dollar", 50.0);
        stub.setBalance(b, "dollar", 100.0);
        stub.putPlayer(a, new EconomyPlayer(a, "Alice", null));
        stub.putPlayer(b, new EconomyPlayer(b, "Bob", null));

        EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new EzEconomyPAPIExpansion.TestEzEconomy() {
            @Override public StorageProvider getStorageOrWarn() { return stub; }
            @Override public String getDefaultCurrency() { return "dollar"; }
            @Override public String format(double amount, String currency) { return String.format(Locale.ROOT, "$%.0f", amount); }
            @Override public String formatShort(double amount, String currency) { return String.format(Locale.ROOT, "$%.0f", amount); }
            @Override public String getCurrencySymbol(String currency) { return "$"; }
            @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
        };

        com.skyblockexp.ezeconomy.cache.CacheManager.getProvider().remove("top:dollar:2");

        String first = expansion.onPlaceholderRequest(null, "top_2_dollar");
        assertNotNull(first);

        String cacheKey = "top:dollar:2";
        var provider = com.skyblockexp.ezeconomy.cache.CacheManager.getProvider();
        var entry = provider.getEntry(cacheKey);
        long deadline = System.currentTimeMillis() + 2000;
        String entryValue = entry == null ? null : String.valueOf(entry.value);
        while ((entry == null || entryValue == null || entryValue.isEmpty() || "loading".equals(entryValue)) && System.currentTimeMillis() < deadline) {
            entry = provider.getEntry(cacheKey);
            entryValue = entry == null ? null : String.valueOf(entry.value);
        }

        assertNotNull(entry, "Expected top cache entry to be populated");
        assertNotNull(entryValue);
        assertTrue(entryValue.contains("Bob") && entryValue.contains("Alice") && entryValue.contains("$100") && entryValue.contains("$50"));

        String third = expansion.onPlaceholderRequest(null, "top_2_dollar");
        assertEquals(entryValue, third);
    }

    // Minimal test StorageProvider to support the tests above
    static class SimpleStorageProvider implements StorageProvider {
        private final Map<UUID, Map<String, Double>> balances = new ConcurrentHashMap<>();
        private final Map<UUID, EconomyPlayer> players = new ConcurrentHashMap<>();
        private final Map<String, Double> banks = new ConcurrentHashMap<>();

        public void putPlayer(UUID id, EconomyPlayer p) { players.put(id, p); }

        @Override public void init() {}
        @Override public void load() {}
        @Override public void save() {}

        @Override
        public double getBalance(UUID uuid, String currency) {
            return balances.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(currency, 0d);
        }

        @Override public void setBalance(UUID uuid, String currency, double amount) { balances.computeIfAbsent(uuid, k->new ConcurrentHashMap<>()).put(currency, amount); }
        @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
        @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return Collections.emptyList(); }
        @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) {
            double cur = getBalance(uuid, currency);
            if (cur < amount) return false;
            setBalance(uuid, currency, cur - amount);
            return true;
        }
        @Override public void deposit(UUID uuid, String currency, double amount) { setBalance(uuid, currency, getBalance(uuid, currency) + amount); }
        @Override public java.util.Map<UUID, Double> getAllBalances(String currency) {
            Map<UUID, Double> out = new HashMap<>();
            balances.forEach((k, v) -> out.put(k, v.getOrDefault(currency, 0d)));
            return out;
        }

        @Override public void shutdown() {}
        @Override public EconomyPlayer getPlayer(UUID uuid) { return players.get(uuid); }
        @Override public boolean createBank(String name, UUID owner) { banks.putIfAbsent(name, 0d); return true; }
        @Override public boolean deleteBank(String name) { return banks.remove(name) != null; }
        @Override public boolean bankExists(String name) { return banks.containsKey(name); }
        @Override public double getBankBalance(String name, String currency) { return banks.getOrDefault(name, 0d); }
        @Override public void setBankBalance(String name, String currency, double amount) { banks.put(name, amount); }
        @Override public boolean tryWithdrawBank(String name, String currency, double amount) { double cur = getBankBalance(name, currency); if (cur < amount) return false; banks.put(name, cur - amount); return true; }
        @Override public void depositBank(String name, String currency, double amount) { banks.put(name, getBankBalance(name, currency) + amount); }
        @Override public java.util.Set<String> getBanks() { return banks.keySet(); }
        @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
        @Override public boolean isBankMember(String name, UUID uuid) { return false; }
        @Override public boolean addBankMember(String name, UUID uuid) { return false; }
        @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
        @Override public java.util.Set<UUID> getBankMembers(String name) { return Collections.emptySet(); }
    }
}



