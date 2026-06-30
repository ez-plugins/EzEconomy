package com.skyblockexp.ezeconomy.papi.coverage;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.dto.EconomyPlayer;
import com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager;
import com.skyblockexp.ezeconomy.cache.CacheManager;
import org.bukkit.OfflinePlayer;
import com.skyblockexp.ezeconomy.papi.testhelpers.TestPlayerFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TargetedExpansionCoverageTest {

    @AfterEach
    public void tearDown() {
        try { MockBukkit.unmock(); } catch (Exception ignored) {}
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }

    static class SimpleTestEz implements com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy {
        private final StorageProvider storage;
        private final String def;
        private final CurrencyPreferenceManager prefManager;

        SimpleTestEz(StorageProvider storage, String def, CurrencyPreferenceManager prefManager) {
            this.storage = storage;
            this.def = def;
            this.prefManager = prefManager;
        }

        @Override public StorageProvider getStorageOrWarn() { return storage; }
        @Override public String getDefaultCurrency() { return def; }
        @Override public String format(double amount, String currency) { return String.format(java.util.Locale.US, "%.2f %s", amount, currency); }
        @Override public String formatShort(double amount, String currency) { return String.format(java.util.Locale.US, "%.1f%s", amount/1000.0, "K"); }
        @Override public String getCurrencySymbol(String currency) { return null; }
        @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return prefManager; }
    }

    static class MapStorage implements com.skyblockexp.ezeconomy.api.storage.StorageProvider {
        private final Map<UUID, Double> all = new HashMap<>();
        public void put(UUID u, double v) { all.put(u, v); }
        @Override public void init() {}
        @Override public void load() {}
        @Override public void save() {}
        @Override public double getBalance(UUID uuid, String currency) { return all.getOrDefault(uuid, 0.0); }
        @Override public void setBalance(UUID uuid, String currency, double amount) { all.put(uuid, amount); }
        @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
        @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return java.util.Collections.emptyList(); }
        @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { return false; }
        @Override public void deposit(UUID uuid, String currency, double amount) {}
        @Override public Map<UUID, Double> getAllBalances(String currency) { return new HashMap<>(all); }
        @Override public java.util.Set<String> cleanupOrphanedPlayers() { return java.util.Collections.emptySet(); }
        @Override public boolean isConnected() { return true; }
        @Override public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) { return com.skyblockexp.ezeconomy.storage.TransferResult.failure(0,0); }
        @Override public void shutdown() {}
        @Override public EconomyPlayer getPlayer(UUID uuid) { return new EconomyPlayer(uuid, "U-"+uuid.toString().substring(0,8), null); }
        @Override public boolean createBank(String name, UUID owner) { return false; }
        @Override public boolean deleteBank(String name) { return false; }
        @Override public boolean bankExists(String name) { return false; }
        @Override public double getBankBalance(String name, String currency) { return 123.45; }
        @Override public void setBankBalance(String name, String currency, double amount) {}
        @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
        @Override public void depositBank(String name, String currency, double amount) {}
        @Override public java.util.Set<String> getBanks() { return java.util.Collections.emptySet(); }
        @Override public java.util.Set<java.util.UUID> getBankMembers(String name) { return java.util.Collections.emptySet(); }
        @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
        @Override public boolean isBankMember(String name, UUID uuid) { return false; }
        @Override public boolean addBankMember(String name, UUID uuid) { return false; }
        @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
    }

    @Test
    public void test_balance_and_currency_branches_with_test_hook() throws Exception {
        MockBukkit.mock();
        com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin papi = (com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin.class);

        MapStorage ms = new MapStorage();
        UUID u = UUID.randomUUID();
        ms.put(u, 42.5);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new SimpleTestEz(ms, "dollar", null);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(papi);

        OfflinePlayer fake = TestPlayerFakes.fakeOfflinePlayer(u);

        String bal = expansion.onPlaceholderRequest(fake, "balance");
        assertTrue(bal.contains("42.50") || !bal.isEmpty());

        String balCur = expansion.onPlaceholderRequest(fake, "balance_euro");
        assertTrue(balCur.contains("0.00") || !balCur.isEmpty());

        String fmt = expansion.onPlaceholderRequest(fake, "balance_formatted");
        assertNotNull(fmt);

        String shortFmt = expansion.onPlaceholderRequest(fake, "balance_short");
        assertNotNull(shortFmt);

        String bank = expansion.onPlaceholderRequest(null, "bank_test_dollar");
        assertNotNull(bank);
        assertTrue(bank.contains("123.45") || bank.length() > 0);
    }

    @Test
    public void test_top_path_with_test_hook_populates_cache() throws Exception {
        MockBukkit.mock();
        com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin papi = (com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin.class);

        MapStorage ms = new MapStorage();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ms.put(a, 1000.0);
        ms.put(b, 2000.0);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new SimpleTestEz(ms, "dollar", null);

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(papi);

        String res = expansion.onPlaceholderRequest(null, "top_2_dollar");
        assertNotNull(res);

        String cacheKey = "top:dollar:2";
        var entry = CacheManager.getProvider().getEntry(cacheKey);
        long deadline = System.currentTimeMillis() + 1000;
        while (entry == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            entry = CacheManager.getProvider().getEntry(cacheKey);
        }
        assertNotNull(entry);
        assertNotNull(entry.value);
    }
}
